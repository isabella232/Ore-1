package controllers.apiv2.helpers

import scala.collection.immutable

import models.protocols.APIV2
import ore.permission.scope.Scope

import enumeratum.{Enum, EnumEntry}
import io.circe._

sealed abstract class APIScope[RealScope <: Scope](val tpe: APIScopeType)
object APIScope {
  case object GlobalScope extends APIScope[ore.permission.scope.GlobalScope.type](APIScopeType.Global)
  case class ProjectScope(projectOwner: String, projectSlug: String)
      extends APIScope[ore.permission.scope.ProjectScope](APIScopeType.Project)
  case class OrganizationScope(organizationName: String)
      extends APIScope[ore.permission.scope.OrganizationScope](APIScopeType.Organization)
}

sealed abstract class APIScopeType extends EnumEntry with EnumEntry.Snakecase
object APIScopeType extends Enum[APIScopeType] {
  case object Global       extends APIScopeType
  case object Project      extends APIScopeType
  case object Organization extends APIScopeType

  val values: immutable.IndexedSeq[APIScopeType] = findValues

  implicit val codec: Codec[APIScopeType] = APIV2.enumCodec(APIScopeType)(_.entryName)
}
