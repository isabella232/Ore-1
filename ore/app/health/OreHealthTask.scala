package health

import java.rmi.registry.LocateRegistry

import discourse.OreDiscourseApi
import ore.auth.SSOApi
import ore.db.ModelService

import zio.clock.Clock
import zio.{Task, UIO, ZManaged, ZSchedule}

object OreHealthTask {

  def program(
      implicit service: ModelService[Task],
      auth: SSOApi[Task],
      forums: OreDiscourseApi[Task]
  ): ZManaged[Clock, Nothing, Unit] = {
    val registry  = LocateRegistry.getRegistry(1888)
    val healthApi = registry.lookup("OreHealthApi").asInstanceOf[OreStatusHolder]

    val postUpdate = OreStatusImpl.createStatus.flatMap { s =>
      Task(healthApi.postStatus(s)).flatMapError(e => UIO(e.printStackTrace())).option.unit
    }

    val interval = zio.duration.Duration(???, ???)

    val schedule = ZSchedule.fixed(interval)

    ZManaged.make(postUpdate.repeat(schedule).fork)(fiber => fiber.interrupt).unit
  }
}
