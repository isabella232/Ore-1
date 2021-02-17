package util

import zio.{Has, UIO, UManaged, URIO, URManaged, ZIO, ZManaged}

trait FiberSentry {

  def withScope(configure: FiberSentry.Scope => FiberSentry.Scope): UManaged[Unit]

  def configureScope(configure: FiberSentry.Scope => FiberSentry.Scope): UIO[Unit]
}
object FiberSentry {

  def withScope(configure: FiberSentry.Scope => FiberSentry.Scope): URManaged[Has[FiberSentry], Unit] =
    ZManaged.accessManaged.apply(_.get.withScope(configure))

  def configureScope(configure: FiberSentry.Scope => FiberSentry.Scope): URIO[Has[FiberSentry], Unit] =
    ZIO.accessM.apply(_.get.configureScope(configure))

  sealed trait Level
  object Level {
    case object Debug   extends Level
    case object Info    extends Level
    case object Warning extends Level
    case object Error   extends Level
    case object Fatal   extends Level
  }

  case class Scope(
      level: Option[FiberSentry.Level],
      transaction: Option[String],
      user: Option[FiberSentry.User],
      newFingerprints: Option[Seq[String]],
      addTags: Map[String, String],
      removeTags: Set[String],
      addExtras: Map[String, String],
      removeExtras: Set[String]
  ) {

    def addTag(key: String, value: String): Scope = copy(addTags = addTags.updated(key, value))
    def removeTag(key: String): Scope             = copy(addTags = addTags.removed(key), removeTags = removeTags + key)

    def addExtra(key: String, value: String): Scope = copy(addExtras = addExtras.updated(key, value))
    def removeExtra(key: String): Scope             = copy(addExtras = addExtras.removed(key), removeExtras = removeExtras + key)
  }

  case class User(
      email: Option[String] = None,
      id: Option[String] = None,
      username: Option[String] = None,
      ipAddress: Option[String] = None,
      other: Map[String, String] = Map.empty
  )
}
