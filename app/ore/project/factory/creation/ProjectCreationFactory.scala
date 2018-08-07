package ore.project.factory.creation

import java.nio.file.Files.{copy, createDirectories, notExists}
import java.nio.file.StandardCopyOption

import akka.actor.ActorSystem
import cats.instances.future._
import db.ModelService
import db.impl.access.{ProjectBase, UserBase}
import discourse.OreDiscourseApi
import javax.inject.Inject
import models.project.{Project, Version, VisibilityTypes}
import models.user.User
import ore.project.io.{PluginFile, PluginUpload, ProjectFiles}
import ore.{OreConfig, OreEnv}
import play.api.cache.SyncCacheApi
import play.api.i18n.Messages
import security.pgp.PGPVerifier

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching.Regex

trait ProjectCreationFactory {

  // Service
  implicit val service: ModelService
  implicit val users: UserBase = this.service.getModelBase(classOf[UserBase])
  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  val fileManager: ProjectFiles = this.projects.fileManager
  val cacheApi: SyncCacheApi
  val actorSystem: ActorSystem // Used for Scheduling Notifications
  val pgpVerifier: PGPVerifier = new PGPVerifier
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
  def createProjectStep1(uploadData: PluginUpload, uploader: User, ownerId: Int)(implicit ec: ExecutionContext, messages: Messages) : Either[String, PendingProjectCreation] = {
    // Check files in PluginUpload
    val filePlugin = uploadData.pluginFileName
    var fileSignature = uploadData.signatureFileName

    if (!filePlugin.endsWith(".zip") && !filePlugin.endsWith(".jar")) {
      return Left("error.plugin.fileExtension")
    }
    if (!fileSignature.endsWith(".asc") && !fileSignature.endsWith(".sig")) {
      return Left("error.plugin.sig.fileExtension")
    }

    // Check PGP Pub Key of uploader
    // TODO: Remove Await
    val pgpValid = Await.result(uploader.isPgpPubKeyReadyForUpload, 10.seconds)
    if (!pgpValid._1) {
      return Left(pgpValid._2)
    }

    // Get paths of PluginUpload
    var pathPlugin = uploadData.pluginFile.path
    var pathSignature = uploadData.signatureFile.path

    // Check signature of uploaded File
    if (!this.pgpVerifier.verifyDetachedSignature(pathPlugin, pathSignature, uploader.pgpPubKey.get)) {
      return Left("error.plugin.sig.failed")
    }

    // Get owner
    val ownerUser = Await.result(this.users.get(ownerId).getOrElse(throw new IllegalArgumentException("None on get")), 10.seconds)

    // Move files to temp folder
    val pathTempUpload = this.env.tmp.resolve(ownerUser.name)

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
    val projectBuild = Project.Builder(this.service)
        .pluginId(metaData.id.get)
        .ownerName(ownerUser.name)
        .ownerId(ownerUser.id.value)
        .name(metaData.get[String]("name").getOrElse("name not found"))
        .visibility(VisibilityTypes.New)
        .build()

    // Start new Version Builder
    val versionBuild = Version.Builder(this.service)
        .versionString(metaData.version.get)
        .dependencyIds(metaData.dependencies.map(d => d.pluginId + ":" + d.version).toList)
        .description(metaData.get[String]("description").getOrElse(""))
        .projectId(projectBuild.id.value) // Version might be for an uncreated project
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
    Right(PendingProjectCreation(
      factory = this,
      underlying = projectBuild,
      file = pluginFile,
      channelName = this.config.getSuggestedNameForVersion(metaData.version.get),
      pendingVersion = pendingVersion,
      cacheApi = this.cacheApi
    ))
  }

}

class OreProjectCreationFactory @Inject()(override val service: ModelService,
                                  override val config: OreConfig,
                                  override val forums: OreDiscourseApi,
                                  override val cacheApi: SyncCacheApi,
                                  override val actorSystem: ActorSystem)
  extends ProjectCreationFactory
