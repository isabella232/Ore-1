package ore.models.project.io

import java.nio.file.{Files, Path}

import ore.db.{DbRef, Model}
import ore.models.project.{Asset, Dependency, Plugin, PluginInfoParser, PluginPlatform, Project, Version}
import ore.models.user.User
import ore.util.StringUtils
import ore.{OreConfig, OrePlatform}

class PluginFileWithData(val path: Path, val user: Model[User], val entries: List[PluginInfoParser.Entry])(
    implicit config: OreConfig
) {

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = StringUtils.md5ToHex(Files.readAllBytes(this.path))

  lazy val allDependencyIds: Seq[String]              = entries.flatMap(_.dependencies).map(_.identifier)
  lazy val allDependencyVersions: Seq[Option[String]] = entries.flatMap(_.dependencies).map(_.rawVersion)

  lazy val platformWarnings: List[String] =
    OrePlatform.createVersionedPlatforms(allDependencyIds, allDependencyVersions).run._1

  def warnings: Seq[String] = platformWarnings

  def asPlugins(
      assetId: DbRef[Asset]
  ): Seq[(Plugin, DbRef[Plugin] => Set[Dependency], DbRef[Plugin] => List[PluginPlatform])] = entries.map { entry =>
    val (dependencyIds, dependencyVersions) = entry.dependencies.map(d => (d.identifier, d.rawVersion)).toSeq.unzip

    val versionedPlatforms = OrePlatform.createVersionedPlatforms(dependencyIds, dependencyVersions).run._2

    (
      Plugin(
        assetId,
        entry.identifier,
        entry.version
      ),
      (pluginId: DbRef[Plugin]) =>
        entry.dependencies.map { dep =>
          Dependency(
            pluginId,
            dep.identifier,
            dep.rawVersion,
            Dependency.VersionSyntax.withValue(dep.versionSyntax),
            dep.required
          )
        },
      (pluginId: DbRef[Plugin]) =>
        versionedPlatforms.map(p => PluginPlatform(pluginId, p.id, p.version, p.coarseVersion))
    )
  }

  //TODO: Support multiple platforms here
  def asVersion(
      versionName: String,
      projectId: DbRef[Project],
      description: Option[String],
      createForumPost: Boolean,
      stability: Version.Stability,
      releaseType: Option[Version.ReleaseType],
      pluginAssetId: DbRef[Asset]
  ): Version = Version(
    name = StringUtils.compact(versionName),
    slug = StringUtils.slugify(StringUtils.compact(versionName)),
    projectId = projectId,
    authorId = Some(user.id),
    description = description,
    createForumPost = createForumPost,
    pluginAssetId = pluginAssetId,
    tags = Version.VersionTags(
      usesMixin = entries.exists(_.mixin),
      stability = stability,
      releaseType = releaseType
    )
  )
}
