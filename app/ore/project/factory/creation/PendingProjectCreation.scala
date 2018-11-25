package ore.project.factory.creation

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.SyncCacheApi

import db.ModelService
import models.project.{Project, ProjectSettings, Version}
import models.user.role.ProjectUserRole
import ore.Cacheable
import ore.project.io.PluginFile

import cats.effect.{ContextShift, IO}

/**
  * Represents a Project with an uploaded plugin that has not yet been
  * created.
  *
  * @param underlying  Pending project
  * @param file     Uploaded plugin
  */
case class PendingProjectCreation(
    factory: ProjectCreationFactory,
    var underlying: Project,
    file: PluginFile,
    channelName: String,
    var pendingVersion: PendingVersionCreation,
    var roles: Set[ProjectUserRole] = Set(),
    override val cacheApi: SyncCacheApi
)(implicit service: ModelService)
    extends Cacheable {

  /**
    * The [[Project]]'s internal settings.
    */
  val settings: ProjectSettings = ProjectSettings()

  def complete()(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[(Project, Version)] = {
    free
    for {
      newProject <- this.factory.createProject(this)
      newVersion <- {
        this.pendingVersion.project = newProject
        this.factory.createVersion(this.pendingVersion)
      }
      updatedProject <- service.update(newProject.copy(recommendedVersionId = Some(newVersion._1.id.value)))
    } yield (updatedProject, newVersion._1)
  }

  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
