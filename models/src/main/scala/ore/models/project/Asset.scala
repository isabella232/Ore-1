package ore.models.project

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.AssetTable

import enumeratum.values.{StringEnum, StringEnumEntry}
import slick.lifted.TableQuery

case class Asset(
    projectId: DbRef[Project],
    versionId: DbRef[Version],
    filename: String,
    isMain: Boolean,
    assetType: Asset.AssetType,
    hash: String,
    filesize: Long,
    visibility: Visibility,
    usesMixin: Boolean
)
object Asset extends DefaultModelCompanion[Asset, AssetTable](TableQuery[AssetTable]) {

  implicit val query: ModelQuery[Asset] = ModelQuery.from(this)

  sealed abstract class AssetType(val value: String) extends StringEnumEntry
  object AssetType extends StringEnum[AssetType] {
    override def values: IndexedSeq[AssetType] = findValues

    object Content         extends AssetType("content")
    object UploadedArchive extends AssetType("uploaded_archive")
    object Source          extends AssetType("source")
    object Miscc           extends AssetType("misc")
  }
}
