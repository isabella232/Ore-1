package db.impl.access

import scala.language.higherKinds

import ore.OreConfig
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{AssetTable, ProjectTable, VersionTable}
import ore.db.{DbRef, Model, ModelService}
import ore.member.Joinable
import ore.models.Job
import ore.models.project._
import ore.models.project.io.ProjectFiles
import ore.models.user.User
import ore.util.StringUtils._
import ore.util.{FileUtils, OreMDC}
import util.FileIO
import util.syntax._

import cats.effect.syntax.all._
import cats.syntax.all._
import cats.tagless.autoFunctorK

@autoFunctorK
trait ProjectBase[+F[_]] {

  def missingFile: F[Seq[(String, String, Option[String], DbRef[Project], DbRef[Asset], String)]]

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String): F[Option[Model[Project]]]

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String): F[Option[Model[Project]]]

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withApiV1Identifier(pluginId: String): F[Option[Model[Project]]]

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String): F[Boolean]

  /**
    * Returns true if the specified project exists.
    *
    * @return True if exists
    */
  def exists(owner: String, name: String): F[Boolean]

  /**
    * Renames the specified [[Project]].
    *
    * @param project  Project to rename
    * @param name     New name to assign Project
    */
  def rename(project: Model[Project], name: String): F[Either[String, Unit]]

  def transfer(project: Model[Project], newOwnerId: DbRef[User]): F[Either[String, Unit]]

  def prepareDeleteVersion(version: Model[Version]): F[Model[Project]]

  /**
    * Irreversibly deletes this version.
    */
  def deleteVersion(version: Model[Version])(implicit mdc: OreMDC): F[Model[Project]]

  /**
    * Irreversibly deletes this project.
    *
    * @param project Project to delete
    */
  def delete(project: Model[Project])(implicit mdc: OreMDC): F[Int]
}

object ProjectBase {

  /**
    * Default live implementation of [[ProjectBase]]
    */
  class ProjectBaseF[F[_]](
      implicit service: ModelService[F],
      config: OreConfig,
      fileManager: ProjectFiles,
      fileIO: FileIO[F],
      F: cats.effect.Effect[F],
      projectJoinable: Joinable[F, Project]
  ) extends ProjectBase[F] {

    def missingFile: F[Seq[(String, String, Option[String], DbRef[Project], DbRef[Asset], String)]] = {
      def allVersions =
        for {
          t <- TableQuery[AssetTable]
            .joinLeft(TableQuery[VersionTable])
            .on((a, v) => v.pluginAssetId === a.id || v.docsAssetId === a.id || v.sourcesAssetId === a.id)
          (a, v) = t
          p <- TableQuery[ProjectTable] if a.projectId === p.id
        } yield (p.ownerName, p.name, v.map(_.name), p.id, a.id, a.filename)

      service.runDBIO(allVersions.result).flatMap { versions =>
        fileIO
          .traverseLimited(versions.toVector) {
            case t @ (_, _, _, projectId, assetId, _) =>
              val res = F
                .attempt(
                  F.delay(this.fileManager.getAssetPath(projectId, assetId)).flatMap(fileIO.notExists)
                )
                .map {
                  case Left(_)      => false //Invalid file name
                  case Right(value) => value
                }

              res.tupleLeft(t)
          }
          .map {
            _.collect {
              case (t, true) => t
            }
          }
      }
    }

    def withName(owner: String, name: String): F[Option[Model[Project]]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)
        .value

    def withSlug(owner: String, slug: String): F[Option[Model[Project]]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)
        .value

    def withApiV1Identifier(pluginId: String): F[Option[Model[Project]]] =
      ModelView.now(Project).find(equalsIgnoreCase(_.apiV1Identifier, pluginId)).value

    def isNamespaceAvailable(owner: String, slug: String): F[Boolean] =
      withSlug(owner, slug).map(_.isEmpty)

    def exists(owner: String, name: String): F[Boolean] =
      withName(owner, name).map(_.isDefined)

    def rename(
        project: Model[Project],
        name: String
    ): F[Either[String, Unit]] = {
      val newName = compact(name)
      val newSlug = slugify(newName)

      val doRename = {
        val renameModel = service.update(project)(_.copy(name = newName))
        val addForumJob = service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob)

        if (project.topicId.isDefined)
          renameModel *> addForumJob.void
        else
          renameModel.void
      }

      if (!config.isValidProjectName(name)) F.pure(Left("Invalid project name"))
      else {
        for {
          isAvailable <- this.isNamespaceAvailable(project.ownerName, newSlug)
          _           <- if (isAvailable) doRename else F.unit
        } yield Either.cond(isAvailable, (), "Name not available")
      }
    }

    def transfer(project: Model[Project], newOwnerId: DbRef[User]): F[Either[String, Unit]] = {
      // Down-grade current owner to "Developer"

      val transferProject = project.transferOwner[F](newOwnerId)
      val addForumJob     = service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob)

      val dbOp =
        if (project.topicId.isDefined)
          transferProject <* addForumJob
        else
          transferProject

      for {
        newOwnerUser <- ModelView
          .now(User)
          .get(newOwnerId)
          .getOrElseF(F.raiseError(new Exception("Could not find user to transfer owner to")))
        isAvailable <- isNamespaceAvailable(newOwnerUser.name, project.slug)
        _           <- if (isAvailable) dbOp else F.unit
      } yield Either.cond(isAvailable, (), "Name not available")
    }

    def prepareDeleteVersion(version: Model[Version]): F[Model[Project]] = {
      for {
        proj <- version.project
        _ <- F
          .pure(version.visibility != Visibility.SoftDelete)
          .ifM(
            proj
              .versions(ModelView.now(Version))
              .count(_.visibility === (Visibility.Public: Visibility))
              .ensure(new IllegalStateException("only one public version"))(_ > 1)
              .void,
            F.unit
          )
      } yield proj
    }

    /**
      * Irreversibly deletes this version.
      */
    def deleteVersion(version: Model[Version])(implicit mdc: OreMDC): F[Model[Project]] = {
      for {
        proj <- prepareDeleteVersion(version)
        _ <- {
          //TODO: Handle other storages than local
          val deleteFiles = fileIO.traverseLimited(
            List(
              Some(fileManager.getAssetPath(version.projectId, version.pluginAssetId)),
              version.docsAssetId.map(fileManager.getAssetPath(version.projectId, _)),
              version.sourcesAssetId.map(fileManager.getAssetPath(version.projectId, _))
            ).flatten
          )(fileIO.deleteIfExists)

          deleteFiles *> service.delete(version)
        }
      } yield proj
    }

    /**
      * Irreversibly deletes this project.
      *
      * @param project Project to delete
      */
    def delete(project: Model[Project])(implicit mdc: OreMDC): F[Int] = {
      val fileEff = fileIO.executeBlocking(
        FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.id))
      )
      val addForumJob = (id: Int) => service.insert(Job.DeleteDiscourseTopic.newJob(id).toJob).void

      val eff = project.topicId.fold(F.unit)(addForumJob)
      eff *> service.delete(project) <* fileEff
    }
  }

  def apply[F[_]](implicit projectBase: ProjectBase[F]): ProjectBase[F] = projectBase
}
