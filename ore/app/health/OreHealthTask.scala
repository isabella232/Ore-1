package health

import java.rmi.registry.LocateRegistry

import discourse.OreDiscourseApi
import ore.OreConfig
import ore.auth.SSOApi
import ore.db.ModelService

import zio.clock.Clock
import zio.{Task, UIO, ZIO, ZManaged, ZSchedule}

object OreHealthTask {

  def program(
      implicit config: OreConfig,
      service: ModelService[Task],
      auth: SSOApi[Task],
      forums: OreDiscourseApi[Task]
  ): ZManaged[Clock, Nothing, Unit] = {
    val registry  = LocateRegistry.getRegistry(1888)
    val healthApi = registry.lookup("OreHealthApi").asInstanceOf[OreStatusHolder]

    val createAndPost: ZIO[Any, Unit, Unit] = OreStatusImpl.createStatus
      .flatMap(s => Task(healthApi.postStatus(s)))
      .flatMapError(e => UIO(e.printStackTrace()))

    val withoutError = createAndPost.option.unit

    val interval = zio.duration.Duration.fromScala(config.health.interval)

    val schedule = ZSchedule.fixed(interval)

    ZManaged.make(withoutError.repeat(schedule).fork)(fiber => fiber.interrupt).unit
  }
}
