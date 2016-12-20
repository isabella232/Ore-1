package models.project

import java.nio.file.Files._

import play.api.Logger

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, ProjectSettingsTable, UserTable}
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import form.project.ProjectSettingsForm
import models.competition.{Competition, CompetitionEntry}
import models.user.{Notification, User}
import ore.permission.role.Role
import ore.project.factory.PendingProject
import ore.project.io.ProjectFiles
import ore.project.{Category, ProjectOwned}
import ore.user.notification.NotificationType
import util.StringUtils._

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Represents a [[Project]]'s settings.
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param projectId    ID of project settings belong to
  * @param homepage     Project homepage
  * @param issues      Project issues URL
  * @param source      Project source URL
  * @param licenseName Project license name
  * @param licenseUrl  Project license URL
  */
case class ProjectSettings(
    id: ObjId[ProjectSettings],
    createdAt: ObjectTimestamp,
    projectId: DbRef[Project],
    homepage: Option[String],
    issues: Option[String],
    source: Option[String],
    licenseName: Option[String],
    licenseUrl: Option[String],
    forumSync: Boolean
) extends Model {

  override type M = ProjectSettings
  override type T = ProjectSettingsTable

  /**
    * Saves a submitted [[ProjectSettingsForm]] to the [[Project]].
    *
    * @param formData Submitted settings
    * @param messages MessagesApi instance
    */
  def save(project: Project, formData: ProjectSettingsForm)(
      implicit fileManager: ProjectFiles,
      service: ModelService,
      cs: ContextShift[IO]
  ): EitherT[IO, NonEmptyList[String], (Project, ProjectSettings)] = EitherT {
    import cats.instances.vector._
    Logger.debug("Saving project settings")
    Logger.debug(formData.toString)
    val newOwnerId = formData.ownerId.getOrElse(project.ownerId)

    val queryOwnerName = TableQuery[UserTable].filter(_.id === newOwnerId).map(_.name)

    val updateProject = service.runDBIO(queryOwnerName.result.head).flatMap { ownerName =>
      service.update(
        project.copy(
          category = Category.values.find(_.title == formData.categoryName).get,
          description = noneIfEmpty(formData.description),
          ownerId = newOwnerId,
          ownerName = ownerName
        )
      )
    }

    val updateSettings = service.update(
      copy(
        issues = noneIfEmpty(formData.issues),
        source = noneIfEmpty(formData.source),
        licenseUrl = noneIfEmpty(formData.licenseUrl),
        licenseName = if (formData.licenseUrl.nonEmpty) Some(formData.licenseName) else licenseName,
        forumSync = formData.forumSync
      )
    )

    val modelUpdates = (updateProject, updateSettings).parTupled

    modelUpdates.flatMap {
      case t @ (newProject, newProjectSettings) =>
        // Update icon
        if (formData.updateIcon) {
          fileManager.getPendingIconPath(newProject).foreach { pendingPath =>
            val iconDir = fileManager.getIconDir(newProject.ownerName, newProject.name)
            if (notExists(iconDir))
              createDirectories(iconDir)
            list(iconDir).forEach(delete(_))
            move(pendingPath, iconDir.resolve(pendingPath.getFileName))
          }
        }

        // Add new roles
        val dossier = newProject.memberships
        formData
          .build()
          .toVector
          .parTraverse { role =>
            dossier.addRole(newProject, role.userId, role.copy(projectId = newProject.id.value).asFunc)
          }
          .flatMap { roles =>
            val notifications = roles.map { role =>
              Notification.partial(
                userId = role.userId,
                originId = newProject.ownerId,
                notificationType = NotificationType.ProjectInvite,
                messageArgs = NonEmptyList.of("notification.project.invite", role.role.title, newProject.name)
              )
            }

            service.bulkInsert(notifications)
          }
          .productR {
            // Update existing roles
            val usersTable = TableQuery[UserTable]
            // Select member userIds
            service
              .runDBIO(usersTable.filter(_.name.inSetBind(formData.userUps)).map(_.id).result)
              .flatMap { userIds =>
                import cats.instances.list._
                val roles = formData.roleUps.traverse { role =>
                  Role.projectRoles
                    .find(_.value == role)
                    .fold(IO.raiseError[Role](new RuntimeException("supplied invalid role type")))(IO.pure)
                }

                roles.map(xs => userIds.zip(xs))
              }
              .map {
                _.map {
                  case (userId, role) => updateMemberShip(userId).update(role)
                }
              }
              .flatMap(updates => service.runDBIO(DBIO.sequence(updates)))
          }
          .productR {
            OptionT
              .fromOption[IO](formData.competitionId)
              .flatMap(service.get[Competition](_))
              .toRight(NonEmptyList.one("not.found"))
              .semiflatMap { comp =>
                val entries               = comp.entries
                val projectAlreadyEntered = entries.exists(_.projectId === this.projectId)
                val projectLimitReached   = entries.count(_.userId === newOwnerId).map(_ >= comp.allowedEntries)
                val competitionCapacityReached =
                  comp.maxEntryTotal.fold(IO.pure(false))(capacity => entries.size.map(_ >= capacity))
                val deadlinePassed = IO.pure(comp.timeRemaining.toSeconds <= 0)
                val onlySpongePlugins =
                  if (comp.isSpongeOnly)
                    newProject.recommendedVersion
                      .semiflatMap(_.tags)
                      .map(_.forall(!_.name.startsWith("Sponge")))
                      .getOrElse(true)
                  else IO.pure(false)
                val onlyVisibleSource =
                  if (comp.isSourceRequired) IO.pure(newProjectSettings.source.isEmpty) else IO.pure(false)

                (
                  projectAlreadyEntered,
                  projectLimitReached,
                  competitionCapacityReached,
                  deadlinePassed,
                  onlySpongePlugins,
                  onlyVisibleSource,
                  IO.pure(comp)
                ).parTupled
              }
              .flatMap {
                case (
                    projectAlreadyEntered,
                    projectLimitReached,
                    competitionCapacityReached,
                    deadlinePassed,
                    onlySpongePlugins,
                    onlyVisibleSource,
                    competition
                    ) =>
                  val errors = Seq(
                    projectAlreadyEntered      -> "error.project.competition.alreadyEntered",
                    projectLimitReached        -> "error.project.competition.entryLimit",
                    competitionCapacityReached -> "error.project.competition.capacity",
                    deadlinePassed             -> "error.project.competition.over",
                    onlySpongePlugins          -> "error.project.competition.spongeOnly",
                    onlyVisibleSource          -> "error.project.competition.sourceOnly"
                  )

                  val applicableErrors = errors.collect {
                    case (pred, msg) if pred => msg
                  }

                  applicableErrors.toList.toNel.fold(
                    EitherT.right[NonEmptyList[String]](
                      service
                        .insert[CompetitionEntry](
                          CompetitionEntry.partial(
                            projectId = this.projectId,
                            userId = newOwnerId,
                            competitionId = competition.id.value
                          )
                        )
                        .as(t)
                    )
                  )(errs => EitherT.leftT(errs))
              }
              .value
          }
    }
  }

  private def memberShipUpdate(userId: Rep[DbRef[User]]) =
    TableQuery[ProjectRoleTable].filter(_.userId === userId).map(_.roleType)

  private lazy val updateMemberShip = Compiled(memberShipUpdate _)
}
object ProjectSettings {
  case class Partial(
      homepage: Option[String] = None,
      issues: Option[String] = None,
      source: Option[String] = None,
      licenseName: Option[String] = None,
      licenseUrl: Option[String] = None,
      forumSync: Boolean = true
  ) {

    /**
      * Saves a submitted [[ProjectSettingsForm]] to the [[PendingProject]].
      *
      * @param formData Submitted settings
      * @param messages MessagesApi instance
      */
    //noinspection ComparingUnrelatedTypes
    def save(project: PendingProject, formData: ProjectSettingsForm)(
        implicit fileManager: ProjectFiles,
        service: ModelService
    ): IO[(PendingProject, Partial)] = {
      val queryOwnerName = for {
        u <- TableQuery[UserTable] if formData.ownerId.getOrElse(project.ownerId).bind === u.id
      } yield u.name

      val updateProject = service.runDBIO(queryOwnerName.result).map { ownerName =>
        val newProj = project.copy(
          category = Category.values.find(_.title == formData.categoryName).get,
          description = noneIfEmpty(formData.description),
          ownerId = formData.ownerId.getOrElse(project.ownerId),
          ownerName = ownerName.head
        )(project.config)

        newProj.pendingVersion = newProj.pendingVersion.copy(projectUrl = newProj.key)

        newProj
      }

      val updatedSettings = copy(
        issues = noneIfEmpty(formData.issues),
        source = noneIfEmpty(formData.source),
        licenseUrl = noneIfEmpty(formData.licenseUrl),
        licenseName = if (formData.licenseUrl.nonEmpty) Some(formData.licenseName) else licenseName,
        forumSync = formData.forumSync
      )

      updateProject.map { project =>
        // Update icon
        if (formData.updateIcon) {
          fileManager.getPendingIconPath(project.ownerName, project.name).foreach { pendingPath =>
            val iconDir = fileManager.getIconDir(project.ownerName, project.name)
            if (notExists(iconDir))
              createDirectories(iconDir)
            list(iconDir).forEach(delete(_))
            move(pendingPath, iconDir.resolve(pendingPath.getFileName))
          }
        }

        (project, updatedSettings)
      }
    }

    def asFunc(projectId: DbRef[Project]): InsertFunc[ProjectSettings] =
      (id, time) => ProjectSettings(id, time, projectId, homepage, issues, source, licenseName, licenseUrl, forumSync)
  }

  implicit val query: ModelQuery[ProjectSettings] =
    ModelQuery.from[ProjectSettings](TableQuery[ProjectSettingsTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[ProjectSettings] = (a: ProjectSettings) => a.projectId
}
