package health

import discourse.OreDiscourseApi
import ore.auth.SSOApi
import ore.db.ModelService
import ore.external.AvailabilityState

import doobie.implicits._
import zio.{Task, UIO}

case class OreStatusImpl(dbState: OreComponentState, authState: OreComponentState, forumState: OreComponentState)
    extends OreStatus
object OreStatusImpl {

  def createStatus(
      implicit service: ModelService[Task],
      auth: SSOApi[Task],
      forums: OreDiscourseApi[Task]
  ): UIO[OreStatus] = {
    val checkDbAvailable = service.runDbCon(sql"SELECT 1".query[Int].unique)

    def toComponentState(e: Either[Throwable, AvailabilityState]): OreComponentState = e match {
      case Right(AvailabilityState.Available)             => OreComponentState.AVAILABLE
      case Right(AvailabilityState.MaybeAvailable)        => OreComponentState.UNKNOWN
      case Right(AvailabilityState.TimedOut)              => OreComponentState.CONNECTION_TIMED_OUT
      case Right(AvailabilityState.Unavailable) | Left(_) => OreComponentState.UNAVAILABLE
    }

    (checkDbAvailable.either <&> auth.isAvailable.either <&> forums.isAvailable.either).map {
      case ((dbState, authState), forumState) =>
        OreStatusImpl(
          dbState match {
            case Right(_) => OreComponentState.AVAILABLE
            case Left(_)  => OreComponentState.UNAVAILABLE //TODO: Figure out which exceptions are returned when
          },
          toComponentState(authState),
          toComponentState(forumState)
        )
    }
  }
}
