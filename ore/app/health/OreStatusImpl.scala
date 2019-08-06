package health

import discourse.OreDiscourseApi
import ore.auth.SSOApi
import ore.db.ModelService

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

    (checkDbAvailable.either <&> auth.isAvailable.either <&> forums.isAvailable.either).map {
      case ((dbState, authState), forumState) =>
        OreStatusImpl(
          dbState match {
            case Right(_) => OreComponentState.AVAILABLE
            case Left(e)  => ??? //TODO: Figure out which exceptions are returned when
          },
          authState match {
            case Right(true) => OreComponentState.AVAILABLE
            case _           => OreComponentState.UNAVAILABLE
          },
          forumState match {
            case Left(_)      => OreComponentState.UNAVAILABLE
            case Right(value) => ???
          }
        )
    }
  }
}
