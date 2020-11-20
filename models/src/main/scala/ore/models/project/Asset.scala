package ore.models.project

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.AssetTable

import enumeratum.values.{StringEnum, StringEnumEntry}
import slick.lifted.TableQuery

case class Asset(
    projectId: DbRef[Project],
    filename: String,
    hash: String,
    filesize: Long
)
object Asset extends DefaultModelCompanion[Asset, AssetTable](TableQuery[AssetTable]) {

  implicit val query: ModelQuery[Asset] = ModelQuery.from(this)
}
