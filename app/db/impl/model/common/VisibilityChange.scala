package db.impl.model.common

import java.sql.Timestamp

import play.api.i18n.Messages
import play.twirl.api.Html

import db.impl.access.UserBase
import db.impl.table.common.VisibilityChangeColumns
import db.{DbRef, Model, ModelService}
import models.project.{Message, Page, Visibility}
import models.user.User
import ore.OreConfig

import cats.data.OptionT
import cats.effect.IO

trait VisibilityChange extends Model { self =>

  type M <: VisibilityChange { type M = self.M }
  type T <: VisibilityChangeColumns[M]

  def createdBy: Option[DbRef[User]]
  def messageId: DbRef[Message]
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[DbRef[User]]
  def visibility: Visibility

  def created(implicit userBase: UserBase): OptionT[IO, User] =
    OptionT.fromOption[IO](createdBy).flatMap(userBase.get(_))

  def comment(implicit service: ModelService, messages: Messages): OptionT[IO, String] =
    service.get[Message](messageId).map(_.format)

  /** Render the comment as Html */
  def renderComment(implicit service: ModelService, messages: Messages, config: OreConfig): OptionT[IO, Html] =
    comment.map(Page.render)

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
