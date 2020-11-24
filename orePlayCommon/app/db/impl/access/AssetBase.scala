package db.impl.access

import java.nio.file.{Files, Path}

import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTable, VersionTable}
import ore.db.{DbRef, Model, ModelService}
import ore.models.project.io.ProjectFiles
import ore.models.project.{Asset, Project}
import ore.util.StringUtils
import util.syntax._

import zio.{RIO, UIO, URIO, ZIO}
import zio.blocking._

trait AssetBase {

  /**
    * Inserts an asset if it is not already present, or returns the existing one if it already exists
    * @param path The asset to insert
    */
  def insertOrGetAsset(projectId: DbRef[Project], path: Path): URIO[Blocking, Model[Asset]]

  /**
    * Checks if an asset already exists in the storage
    * @param path The asset to check against
    */
  def assetExists(projectId: DbRef[Project], path: Path): URIO[Blocking, Boolean]

  /**
    * Delete an existing asset. If the file is for some reason already deleted,
    * that step is skipped.
    */
  def deleteAssetIfUnused(projectId: DbRef[Project], assetId: DbRef[Asset]): URIO[Blocking, Unit]

  /**
    * Deletes an old asset if it's unused, and inserts a new one instead.
    * Might be more efficient than `deleteAssetIfUnused(projectId, assetId) *> insertOrGetAsset(projectId, newPath)`
    * if the old and new asset are the same.
    * @param newPath The potential new asset
    */
  def deleteWithReplacement(
      projectId: DbRef[Project],
      assetId: DbRef[Asset],
      newPath: Path
  ): URIO[Blocking, Model[Asset]]

  /**
    * Delete all the backing assets for a specific project. The actual DB
    * entries may remain because of foreign keys.
    */
  def deleteProjectAssets(projectId: DbRef[Project]): URIO[Blocking, Unit]

  /**
    * Finds all the assets with missing files.
    */
  def missingFileAssets: URIO[Blocking, Seq[(Model[Project], Model[Asset])]]
}
object AssetBase {

  class LocalAssetBase(implicit files: ProjectFiles, service: ModelService[UIO]) extends AssetBase {
    private def md5(path: Path): RIO[Blocking, String] = effectBlocking(StringUtils.md5ToHex(Files.readAllBytes(path)))

    private def fileSize(path: Path): RIO[Blocking, Long] = effectBlocking(Files.size(path))

    private def fileName(path: Path): String = path.getFileName.toString

    override def insertOrGetAsset(projectId: DbRef[Project], path: Path): URIO[Blocking, Model[Asset]] = {
      val ret = for {
        hash <- md5(path)
        res <- ModelView
          .now(Asset)
          .find(_.hash === hash)
          .toZIO
          .orElse {
            for {
              fileSize <- fileSize(path)
              asset    <- service.insert(Asset(projectId, fileName(path), hash, fileSize))
              _ <- ZIO.whenM(effectBlocking(Files.notExists(path.getParent))) {
                effectBlocking(Files.createDirectories(path.getParent))
              }
              _ <- effectBlocking(Files.copy(path, files.getAssetPath(projectId, asset.id)))
            } yield asset
          }
      } yield res

      ret.orDie
    }

    override def assetExists(projectId: DbRef[Project], path: Path): URIO[Blocking, Boolean] =
      md5(path).flatMap(hash => ModelView.now(Asset).exists(_.hash === hash)).orDie

    override def deleteAssetIfUnused(projectId: DbRef[Project], assetId: DbRef[Asset]): URIO[Blocking, Unit] = {
      val usedInVersions = for {
        v <- TableQuery[VersionTable]
        if v.pluginAssetId === assetId || v.docsAssetId === assetId || v.sourcesAssetId === assetId
      } yield v

      val usedInIcons = for {
        p <- TableQuery[ProjectTable]
        if p.iconAssetId === assetId
      } yield p

      val res = ZIO.whenM(service.runDBIO(Query(!usedInVersions.exists && !usedInIcons.exists).result.head)) {
        effectBlocking(Files.deleteIfExists(files.getAssetPath(projectId, assetId))) *>
          service.deleteWhere(Asset)(_.id === assetId)
      }

      res.orDie.unit
    }

    override def deleteWithReplacement(
        projectId: DbRef[Project],
        assetId: DbRef[Asset],
        newPath: Path
    ): URIO[Blocking, Model[Asset]] = {
      val ret = for {
        hash <- md5(newPath)
        res <- ModelView
          .now(Asset)
          .find(_.hash === hash)
          .toZIO
          .orElse(deleteAssetIfUnused(projectId, assetId) *> insertOrGetAsset(projectId, newPath))
      } yield res

      ret.orDie
    }

    override def deleteProjectAssets(projectId: DbRef[Project]): URIO[Blocking, Unit] =
      effectBlocking(Files.deleteIfExists(files.getProjectDir(projectId))).orDie.unit

    override def missingFileAssets: URIO[Blocking, Seq[(Model[Project], Model[Asset])]] = UIO.succeed(Nil) //TODO
  }
}
