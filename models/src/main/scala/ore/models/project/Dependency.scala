package ore.models.project

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.DependencyTable

import enumeratum.values.{NoSuchMember, StringEnum, StringEnumEntry, ValueEnumEntry}
import slick.lifted.TableQuery

case class Dependency(
    pluginId: DbRef[Plugin],
    identifier: String,
    versionRange: Option[String],
    versionSyntax: Dependency.VersionSyntax,
    required: Boolean
)
object Dependency extends DefaultModelCompanion[Dependency, DependencyTable](TableQuery[DependencyTable]) {
  implicit val query: ModelQuery[Dependency] = ModelQuery.from(this)

  sealed abstract class VersionSyntax(val value: String) extends StringEnumEntry
  object VersionSyntax extends StringEnum[VersionSyntax] {
    override def values: IndexedSeq[VersionSyntax] = findValues

    case object Maven             extends VersionSyntax("maven")
    case object Exact             extends VersionSyntax("exact")
    case class Unknown(s: String) extends VersionSyntax(s)

    override def withValue(i: String): VersionSyntax = withValueOpt(i).get

    override def withValueOpt(i: String): Option[VersionSyntax] = Some(super.withValueOpt(i).getOrElse(Unknown(i)))

    override def withValueEither(i: String): Either[NoSuchMember[String, ValueEnumEntry[String]], VersionSyntax] =
      Right(super.withValueEither(i).getOrElse(Unknown(i)))
  }
}
