package db.impl

import db.impl.access.ProjectBase
import ore.OreConfig
import ore.util.OreMDC

import com.typesafe.scalalogging
import zio.clock.Clock
import zio._

object DbUpdateTask {

  def program(config: OreConfig)(implicit projects: ProjectBase[Task]): ZManaged[Clock, Nothing, Unit] = {

    val interval: duration.Duration = duration.Duration.fromScala(config.ore.homepage.updateInterval)

    val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
    implicit val mdc: OreMDC = OreMDC.NoMDC

    val schedule: ZSchedule[Clock, Any, Int] = ZSchedule
      .fixed(interval)
      .logInput(_ => UIO(Logger.debug(s"Updating homepage view")))

    val task = projects.refreshHomePage(Logger).option.unit.repeat(schedule).fork

    ZManaged
      .make(UIO(Logger.info("DbUpdateTask starting")) *> task <* UIO(Logger.info("DbUpdateTask started")))(
        fiber => fiber.interrupt
      )
      .unit
  }
}
