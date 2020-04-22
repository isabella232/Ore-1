package ore.models.project.io

import java.nio.file.{Files, Path}

import ore.data.{Platform, VersionedPlatform}
import ore.db.{DbRef, Model}
import ore.models.project.{Project, Version, VersionPlatform}
import ore.models.user.User
import ore.util.StringUtils

import cats.effect.Sync

class PluginFileWithData(val path: Path, val user: Model[User], val data: PluginFileData) {

  def delete[F[_]](implicit F: Sync[F]): F[Unit] = F.delay(Files.delete(path))

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = StringUtils.md5ToHex(Files.readAllBytes(this.path))

  lazy val fileSize: Long = Files.size(path)

  lazy val fileName: String = path.getFileName.toString

  lazy val dependencyIds: Seq[String]              = data.dependencies.map(_.pluginId)
  lazy val dependencyVersions: Seq[Option[String]] = data.dependencies.map(_.version)

  lazy val versionName: String = StringUtils.compact(data.version.get)
  lazy val versionSlug: String = StringUtils.slugify(data.version.get)

  lazy val (platformWarnings: List[String], versionedPlatforms: List[VersionedPlatform]) =
    Platform.createVersionedPlatforms(dependencyIds, dependencyVersions).run

  def warnings: Seq[String] = platformWarnings

  //TODO: Support multiple platforms here
  def asVersion(
      projectId: DbRef[Project],
      description: Option[String],
      createForumPost: Boolean,
      stability: Version.Stability,
      releaseType: Option[Version.ReleaseType]
  ): Version = Version(
    name = versionName,
    slug = versionSlug,
    projectId = projectId,
    authorId = Some(user.id),
    description = description,
    createForumPost = createForumPost,
    tags = Version.VersionTags(
      stability = stability,
      releaseType = releaseType
    )
  )

  def asPlatforms(versionId: DbRef[Version]): List[VersionPlatform] =
    versionedPlatforms.map(p => VersionPlatform(versionId, p.id, p.version, p.coarseVersion))
}
