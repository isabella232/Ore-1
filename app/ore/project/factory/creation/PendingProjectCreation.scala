package ore.project.factory.creation

import db.ModelService
import db.impl.access.ProjectBase
import models.project.{Project, ProjectSettings, Version}
import models.user.role.ProjectRole
import ore.project.factory.{PendingVersion, ProjectFactory}
import ore.project.io.PluginFile
import ore.{Cacheable, OreConfig}
import play.api.cache.SyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a Project with an uploaded plugin that has not yet been
  * created.
  *
  * @param underlying  Pending project
  * @param file     Uploaded plugin
  */
case class PendingProjectCreation(factory: ProjectCreationFactory,
                                  underlying: Project,
                                  file: PluginFile,
                                  channelName: String,
                                  var pendingVersion: PendingVersionCreation,
                                  var roles: Set[ProjectRole] = Set(),
                                  override val cacheApi: SyncCacheApi)
                                  (implicit service: ModelService)
                                   extends Cacheable {

  /**
    * The [[Project]]'s internal settings.
    */
  val settings: ProjectSettings = ProjectSettings()

  /*
  def complete()(implicit ec: ExecutionContext): Future[(Project, Version)] = {
    free()
    for {
      newProject <- this.factory.createProject(this)
      newVersion <- {
        this.pendingVersion.project = newProject
        this.factory.createVersion(this.pendingVersion)
      }
      _ <- newProject.setRecommendedVersion(newVersion._1)
    } yield (newProject, newVersion._1)
  }
  */


  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
