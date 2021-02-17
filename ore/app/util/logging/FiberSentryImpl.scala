package util.logging

import scala.jdk.CollectionConverters._

import util.FiberSentry

import io.sentry.{IHub, SentryLevel}
import zio._

class FiberSentryImpl(hubs: FiberRef[IHub]) extends FiberSentry {

  override def withScope(configure: FiberSentry.Scope => FiberSentry.Scope): UManaged[Unit] =
    hubs.get.toManaged_.flatMap { hub =>
      val newScope = ZManaged.make(ZIO.effectTotal(hub.pushScope()))(_ => ZIO.effectTotal(hub.popScope()))
      newScope.mapM(_ => configureScope(configure))
    }

  override def configureScope(configure: FiberSentry.Scope => FiberSentry.Scope): UIO[Unit] = {
    hubs.get.flatMap { hub =>
      ZIO.effectTotal(hub.configureScope { scope =>
        val startScope = FiberSentry.Scope(
          Option(scope.getLevel).map(sentryLevelToZioSentry),
          Option(scope.getTransaction),
          Option(scope.getUser).map(sentryUserToZioSentry),
          None,
          Map.empty,
          Set.empty,
          Map.empty,
          Set.empty
        )
        val configuredScope = configure(startScope)
        if (configuredScope.level != startScope.level) {
          scope.setLevel(configuredScope.level.map(zioSentryLevelToSentry).orNull)
        }

        if (configuredScope.transaction != startScope.transaction) {
          scope.setTransaction(configuredScope.transaction.orNull)
        }

        if (configuredScope.user != startScope.user) {
          scope.setUser(configuredScope.user.map(zioSentryUserToSentry).orNull)
        }

        configuredScope.newFingerprints.foreach(fingerprints => scope.setFingerprint(fingerprints.asJava))

        configuredScope.addTags.foreachEntry((k, v) => scope.setTag(k, v))
        configuredScope.removeTags.foreach(scope.removeTag)

        configuredScope.addExtras.foreachEntry((k, v) => scope.setExtra(k, v))
        configuredScope.removeExtras.foreach(scope.removeExtra)
      })
    }
  }

  private def sentryLevelToZioSentry(level: SentryLevel): FiberSentry.Level = level match {
    case SentryLevel.DEBUG   => FiberSentry.Level.Debug
    case SentryLevel.INFO    => FiberSentry.Level.Info
    case SentryLevel.WARNING => FiberSentry.Level.Warning
    case SentryLevel.ERROR   => FiberSentry.Level.Error
    case SentryLevel.FATAL   => FiberSentry.Level.Fatal
  }

  private def zioSentryLevelToSentry(level: FiberSentry.Level): SentryLevel = level match {
    case FiberSentry.Level.Debug   => SentryLevel.DEBUG
    case FiberSentry.Level.Info    => SentryLevel.INFO
    case FiberSentry.Level.Warning => SentryLevel.WARNING
    case FiberSentry.Level.Error   => SentryLevel.ERROR
    case FiberSentry.Level.Fatal   => SentryLevel.FATAL
  }

  private def sentryUserToZioSentry(user: io.sentry.protocol.User): FiberSentry.User =
    FiberSentry.User(
      Option(user.getEmail),
      Option(user.getId),
      Option(user.getUsername),
      Option(user.getIpAddress),
      user.getOthers.asScala.toMap
    )

  private def zioSentryUserToSentry(user: FiberSentry.User): io.sentry.protocol.User = {
    val sentryUser = new io.sentry.protocol.User
    sentryUser.setEmail(user.email.orNull)
    sentryUser.setId(user.id.orNull)
    sentryUser.setUsername(user.username.orNull)
    sentryUser.setIpAddress(user.ipAddress.orNull)
    sentryUser.setOthers(user.other.asJava)

    sentryUser
  }
}
object FiberSentryImpl {

  private[logging] var _hubs: ThreadLocal[IHub] = _
  private var fiberRef: FiberRef[IHub]          = _

  //noinspection AccessorLikeMethodIsEmptyParen
  def getCurrentHub(): IHub = _hubs.get()

  start(Class.forName("io.sentry.NoOpHub").getMethod("getInstance").invoke(null).asInstanceOf[IHub])

  private val cloneMethod = classOf[IHub].getMethod("clone")

  def cloneHub(hub: IHub): IHub =
    //This is stupid
    cloneMethod.invoke(hub).asInstanceOf[IHub]

  private[logging] def start(initialHub: IHub): Unit = {
    zio.Runtime.global.unsafeRun(
      FiberRef
        .make[IHub](initialHub, oldHub => {
          val newHub = cloneHub(oldHub)
          newHub.pushScope()
          newHub
        }, (a, _) => a)
        .flatMap { hubs =>
          hubs.unsafeAsThreadLocal.flatMap(hubsThreadLocal =>
            ZIO
              .effectTotal {
                _hubs = hubsThreadLocal
                fiberRef = hubs
              }
          )
        }
    )
  }

  def layer: ZLayer[Any, Nothing, Has[FiberSentry]] =
    ZIO.effectTotal(fiberRef).map(new FiberSentryImpl(_)).toLayer
}
