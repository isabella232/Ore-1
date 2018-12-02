package models.project

import scala.collection.immutable

import db.ModelFilter
import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Hideable
import ore.permission.{Permission, ReviewProjects}

import enumeratum.values._

sealed abstract class Visibility(
    val value: Int,
    val title: String,
    val permission: Permission,
    val needsReason: Boolean,
    val cssClass: String
) extends IntEnumEntry
object Visibility extends IntEnum[Visibility] {

  val values: immutable.IndexedSeq[Visibility] = findValues

  case object Public extends Visibility(1, "Public", ReviewProjects, needsReason = false, "")
  case object New    extends Visibility(2, "New", ReviewProjects, needsReason = false, "project-new")
  case object NeedsChanges
      extends Visibility(3, "NeedsChanges", ReviewProjects, needsReason = true, "striped project-needsChanges")
  case object NeedsApproval
      extends Visibility(4, "NeedsApproval", ReviewProjects, needsReason = false, "striped project-needsChanges")
  case object SoftDelete
      extends Visibility(5, "SoftDelete", ReviewProjects, needsReason = true, "striped project-hidden")

  def isPublic(visibility: Visibility): Boolean = visibility == Public || visibility == New

  def isPublicFilter[H <: Hideable]: H#T => Rep[Boolean] =
    ModelFilter[H](_.visibility === (Public: Visibility)) || ModelFilter[H](_.visibility === (New: Visibility))
}
