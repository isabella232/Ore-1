package ore.project.factory.creation

import java.nio.file.Files._
import java.nio.file.StandardCopyOption
import javax.inject.Inject

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.matching.Regex

import play.api.cache.SyncCacheApi
import play.api.i18n.Messages

import db.ModelService
import db.impl.access.{ProjectBase, UserBase}
import discourse.OreDiscourseApi
import models.project._
import models.user.role.ProjectUserRole
import models.user.{Notification, User}
import ore.permission.role.Role
import ore.project.NotifyWatchersTask
import ore.project.io._
import ore.user.notification.NotificationType
import ore.{Color, OreConfig, OreEnv, Platform}
import security.pgp.PGPVerifier
import util.StringUtils.equalsIgnoreCase

import akka.actor.ActorSystem
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.instances.vector._
import cats.instances.option._
import cats.syntax.all._
import com.google.common.base.Preconditions.{checkArgument, checkNotNull, checkState}

trait ProjectCreationFactory {

  // Service
  implicit val service: ModelService
  implicit val users: UserBase       = this.service.userBase
  implicit val projects: ProjectBase = this.service.projectBase

  val fileManager: ProjectFiles = this.projects.fileManager
  val cacheApi: SyncCacheApi
  val actorSystem: ActorSystem // Used for Scheduling Notifications
  val pgpVerifier: PGPVerifier      = new PGPVerifier
  val dependencyVersionRegex: Regex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit val config: OreConfig
  implicit val forums: OreDiscourseApi
  implicit val env: OreEnv = this.fileManager.env

  /**
    * Check if PluginUpload contains .jar .zip and .sig .asc
    * Check if PGP Key of PluginUpload matches with the uploader's key
    * Get owner
    * Moves files to temp path
    * Checks metadata in plugin
    * Creates PendingProject
    *
    * @param uploadData
    * @param uploader
    * @param ownerId
    * @return
    */
  def createProjectStep1(uploadData: PluginUpload, uploader: User, ownerId: Long)(
      implicit ec: ExecutionContext,
      messages: Messages
  ): Either[String, PendingProjectCreation] = {
    // Check files in PluginUpload
    val filePlugin    = uploadData.pluginFileName
    var fileSignature = uploadData.signatureFileName

    if (!filePlugin.endsWith(".zip") && !filePlugin.endsWith(".jar")) {
      return Left("error.plugin.fileExtension")
    }
    if (!fileSignature.endsWith(".asc") && !fileSignature.endsWith(".sig")) {
      return Left("error.plugin.sig.fileExtension")
    }

    uploader.isPgpPubKeyReadyForUpload.value.map {
    case Left(error) => return Left(error)
    case Right(())   =>
      // Get paths of PluginUpload
      var pathPlugin    = uploadData.pluginFile.path
      var pathSignature = uploadData.signatureFile.path

      // Check signature of uploaded File
      if (!this.pgpVerifier.verifyDetachedSignature(pathPlugin, pathSignature, uploader.pgpPubKey.get)) {
        return Left("error.plugin.sig.failed")
      }

      for {
        owner <- this.users.get(ownerId)
      } yield {
        // Move files to temp folder
        val pathTempUpload = this.env.tmp.resolve(owner.name)

        if (notExists(pathTempUpload)) {
          createDirectories(pathTempUpload)
        }

        // Rename signature file
        val signatureFileExtension = fileSignature.substring(fileSignature.lastIndexOf("."))
        fileSignature = filePlugin + signatureFileExtension

        // Copy files
        pathPlugin = copy(pathPlugin, pathTempUpload.resolve(filePlugin), StandardCopyOption.REPLACE_EXISTING)
        pathSignature = copy(pathSignature, pathTempUpload.resolve(fileSignature), StandardCopyOption.REPLACE_EXISTING)

        // Make PluginFile
        val pluginFile = new PluginFile(pathPlugin, pathSignature, uploader)

        // Load metadata
        val metaLoad = pluginFile.loadMeta()

        // Check metaLoad
        if (metaLoad.isLeft) {
          return Left(metaLoad.left.get)
        }

        // Sanity check for if data is loaded
        pluginFile.data.getOrElse(throw new IllegalStateException("plugin metadata not loaded?"))

        // Get metaData
        val metaData = metaLoad.right.get

        // Start new Project Builder
        val projectBuild = Project
          .Builder(this.service)
          .pluginId(metaData.id.get)
          .ownerName(owner.name)
          .ownerId(owner.id.value)
          .name(metaData.get[String]("name").getOrElse("name not found"))
          .visibility(Visibility.New)
          .build()

        // Start new Version Builder
        val versionBuild = Version
          .Builder(this.service)
          .versionString(metaData.version.get)
          .dependencyIds(metaData.dependencies.map(d => d.pluginId + ":" + d.version).toList)
          .description(metaData.get[String]("description").getOrElse(""))
          .projectId(projectBuild.id.unsafeToOption.getOrElse(-1)) // Version might be for an uncreated project
          .fileSize(pathPlugin.toFile.length)
          .hash(pluginFile.md5)
          .fileName(pathPlugin.getFileName.toString)
          .signatureFileName(pathSignature.getFileName.toString)
          .authorId(ownerId)
          .build()

        // Make PendingVersion
        val pendingVersion = PendingVersionCreation(
          factory = this,
          project = projectBuild,
          channelName = this.config.getSuggestedNameForVersion(metaData.version.get),
          channelColor = this.config.defaultChannelColor,
          underlying = versionBuild,
          plugin = pluginFile,
          createForumPost = true,
          cacheApi = this.cacheApi
        )

        // Make PendingProject
        Right(
          PendingProjectCreation(
            factory = this,
            underlying = projectBuild,
            file = pluginFile,
            channelName = this.config.getSuggestedNameForVersion(metaData.version.get),
            pendingVersion = pendingVersion,
            cacheApi = this.cacheApi
          )
        )
      }
    }
  }

  //TODO: Remove duplicated code
  //#region DUPLICATE

  /**
    * Creates a new Project from the specified PendingProject
    *
    * @param pending PendingProject
    * @return New Project
    * @throws         IllegalArgumentException if the project already exists
    */
  def createProject(pending: PendingProjectCreation)(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[Project] = {
    val project = pending.underlying

    for {
      (exists, available) <- (
        this.projects.exists(project),
        this.projects.isNamespaceAvailable(project.ownerName, project.slug)
      ).tupled
      _ = checkArgument(!exists, "project already exists", "")
      _ = checkArgument(available, "slug not available", "")
      _ = checkArgument(this.config.isValidProjectName(pending.underlying.name), "invalid name", "")
      // Create the project and it's settings
      newProject <- this.projects.add(pending.underlying)
      _          <- newProject.updateSettings(pending.settings)
      _ <- {
        // Invite members
        val dossier   = newProject.memberships
        val owner     = newProject.owner
        val ownerId   = owner.userId
        val projectId = newProject.id.value

        val addRole =
          dossier.addRole(
            newProject,
            new ProjectUserRole(ownerId, Role.ProjectOwner, projectId, accepted = true, visible = true)
          )

        val addOtherRoles = pending.roles.toVector.parTraverse { userRole =>
          this.service
            .get[User](userRole.userId)
            .map { user =>
              dossier.addRole(newProject, userRole.copy(projectId = projectId)) *>
                user.sendNotification(
                  Notification(
                    userId = user.id.value,
                    originId = ownerId,
                    notificationType = NotificationType.ProjectInvite,
                    messageArgs = NonEmptyList.of("notification.project.invite", userRole.role.title, project.name)
                  )
                )
            }
            .getOrElse {
              //todo
            }
        }

        addRole *> addOtherRoles
      }
      withTopicId <- this.forums.createProjectTopic(newProject)
    } yield withTopicId
  }

  /**
    * Creates a new release channel for the specified [[Project]].
    *
    * @param project Project to create channel for
    * @param name    Channel name
    * @param color   Channel color
    * @return New channel
    */
  def createChannel(project: Project, name: String, color: Color)(implicit ec: ExecutionContext): IO[Channel] = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    checkNotNull(name, "null name", "")
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    checkNotNull(color, "null color", "")
    for {
      channelCount <- project.channels.size
      _ = checkState(channelCount < this.config.ore.projects.maxChannels, "channel limit reached", "")
      channel <- this.service.access[Channel]().add(new Channel(name, color, project.id.value))
    } yield channel
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending PendingVersion
    * @return New version
    */
  def createVersion(
      pending: PendingVersionCreation
  )(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[(Version, Channel, Seq[VersionTag])] = {
    val project = pending.project

    val pendingVersion = pending.underlying

    for {
      // Create channel if not exists
      (channel, exists) <- (getOrCreateChannel(pending, project), pendingVersion.exists).tupled
      _ = if (exists && this.config.ore.projects.fileValidate)
        throw new IllegalArgumentException("Version already exists.")
      // Create version
      newVersion <- {
        val newVersion = Version(
          versionString = pendingVersion.versionString,
          dependencyIds = pendingVersion.dependencyIds,
          description = pendingVersion.description,
          projectId = project.id.value,
          channelId = channel.id.value,
          fileSize = pendingVersion.fileSize,
          hash = pendingVersion.hash,
          authorId = pendingVersion.authorId,
          fileName = pendingVersion.fileName,
          signatureFileName = pendingVersion.signatureFileName
        )
        this.service.access[Version]().add(newVersion)
      }
      tags <- addTags(pending, newVersion)
      // Notify watchers
      _ = this.actorSystem.scheduler.scheduleOnce(Duration.Zero, NotifyWatchersTask(newVersion, project))
      _ <- uploadPlugin(project, pending.plugin, newVersion)
      _ <- if (project.topicId.isDefined && pending.createForumPost)
        this.forums.postVersionRelease(project, newVersion, newVersion.description).void
      else
        IO.unit
    } yield (newVersion, channel, tags)
  }

  private def getOrCreateChannel(pending: PendingVersionCreation, project: Project)(implicit ec: ExecutionContext) =
    project.channels
      .find(equalsIgnoreCase(_.name, pending.channelName))
      .getOrElseF(createChannel(project, pending.channelName, pending.channelColor))

  private def addTags(pendingVersion: PendingVersionCreation, newVersion: Version)(
      implicit ec: ExecutionContext
  ): IO[Seq[VersionTag]] = {
    for {
      (metadataTags, dependencyTags) <- (
        addMetadataTags(pendingVersion.plugin.data, newVersion),
        addDependencyTags(newVersion)
      ).tupled
    } yield {
      metadataTags ++ dependencyTags
    }
  }

  private def addMetadataTags(pluginFileData: Option[PluginFileData], version: Version)(
      implicit ec: ExecutionContext
  ): IO[Seq[VersionTag]] =
    pluginFileData.traverse(_.createTags(version.id.value)).map(_.toList.flatten)

  private def addDependencyTags(version: Version): IO[Seq[VersionTag]] =
    Platform
      .createPlatformTags(
        version.id.value,
        // filter valid dependency versions
        version.dependencies.filter(d => dependencyVersionRegex.pattern.matcher(d.version).matches())
      )

  private def uploadPlugin(project: Project, plugin: PluginFile, version: Version): IO[Unit] = IO {
    val oldPath    = plugin.path
    val oldSigPath = plugin.signaturePath

    val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
    val newPath    = versionDir.resolve(oldPath.getFileName)
    val newSigPath = versionDir.resolve(oldSigPath.getFileName)

    if (exists(newPath) || exists(newSigPath))
      throw InvalidPluginFileException("error.plugin.fileName")
    if (!exists(newPath.getParent))
      createDirectories(newPath.getParent)

    move(oldPath, newPath)
    move(oldSigPath, newSigPath)
    deleteIfExists(oldPath)
    deleteIfExists(oldSigPath)
    ()
  }
  //#endregion
}

class OreProjectCreationFactory @Inject()(
    override val service: ModelService,
    override val config: OreConfig,
    override val forums: OreDiscourseApi,
    override val cacheApi: SyncCacheApi,
    override val actorSystem: ActorSystem
) extends ProjectCreationFactory
