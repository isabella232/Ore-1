package ore.models.user

import java.time.Instant
import java.util.concurrent.TimeUnit

import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.models.api.ApiSession

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.duration.Duration
import zio.{Task, UIO, ZIO, ZManaged, ZSchedule}

object UserTask {

  def program(config: OreConfig)(implicit service: ModelService[Task]): ZManaged[Clock, Nothing, Unit] = {
    val Logger = scalalogging.Logger("UserTask")

    val interval: Duration = zio.duration.Duration.fromScala(config.ore.api.session.checkInterval)

    val action = ZIO
      .accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS))
      .map(Instant.ofEpochMilli)
      .flatMap(now => service.deleteWhere(ApiSession)(_.expires < now))
      .unit

    val schedule: ZSchedule[Clock, Any, Int] = ZSchedule.fixed(interval)

    val task = action.option.unit.repeat(schedule).fork

    ZManaged
      .make(UIO(Logger.info("UserTask starting")) *> task <* UIO(Logger.info("UserTask started")))(
        fiber => fiber.interrupt
      )
      .unit
  }
}
