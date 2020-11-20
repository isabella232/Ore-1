package ore.models.project.io

import java.nio.file.Path

import ore.OreEnv
import ore.db.DbRef
import ore.models.project.{Asset, Project}

/**
  * Handles file management of Projects.
  */
trait ProjectFiles {

  /**
    * Get the folder where all the assets are stored.
    */
  def assetsDir: Path

  /**
    * Gets the folder for a specific project.
    */
  def getProjectDir(projectId: DbRef[Project]): Path

  /**
    * Get the path pointing to a specific asset.
    */
  def getAssetPath(projectId: DbRef[Project], assetId: DbRef[Asset]): Path
}
object ProjectFiles {

  class LocalProjectFiles(val env: OreEnv) extends ProjectFiles {

    override def assetsDir: Path = this.env.plugins.resolve("assets")

    override def getProjectDir(projectId: DbRef[Project]): Path = assetsDir.resolve(projectId.toString)

    override def getAssetPath(projectId: DbRef[Project], assetId: DbRef[Asset]): Path =
      getProjectDir(projectId).resolve(assetId.toString)
  }
}
