package ore.models.project

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.PluginPlatformTable
import ore.db.{DbRef, ModelQuery}

import slick.lifted.TableQuery

case class PluginPlatform(
    pluginId: DbRef[Plugin],
    platform: String,
    platformVersion: Option[String],
    platformCoarseVersion: Option[String]
)
object PluginPlatform
    extends DefaultModelCompanion[PluginPlatform, PluginPlatformTable](TableQuery[PluginPlatformTable]) {

  implicit val query: ModelQuery[PluginPlatform] = ModelQuery.from(this)
}
