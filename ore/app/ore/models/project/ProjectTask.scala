package ore.models.project

import java.time.Instant
import java.util.concurrent.TimeUnit

import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTableMain, VersionTable}

import com.typesafe.scalalogging
import zio.clock.Clock
import zio._

/**
  * Task that is responsible for publishing New projects
  */
object ProjectTask {

  def program(config: OreConfig)(implicit service: ModelService[Task]): ZManaged[Clock, Nothing, Unit] = {
    val Logger = scalalogging.Logger("ProjectTask")

    val interval    = duration.Duration.fromScala(config.ore.projects.checkInterval)
    val draftExpire = config.ore.projects.draftExpire.toMillis

    val draftExpireInstantF =
      ZIO.accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS)).map(_ - draftExpire).map(Instant.ofEpochMilli)

    val newFilter        = ModelFilter(Project)(_.visibility === (Visibility.New: Visibility))
    val hasVersions      = ModelFilter(Project)(p => TableQuery[VersionTable].filter(_.projectId === p.id).exists)
    val createdAtFilterF = draftExpireInstantF.map(dayAgo => ModelFilter(Project)(_.createdAt < dayAgo))

    val updateFalseNewProjects = service.runDBIO(
      TableQuery[ProjectTableMain].filter(newFilter && hasVersions).map(_.visibility).update(Visibility.Public)
    )

    val deleteNewProjects =
      createdAtFilterF.flatMap(createdAtFilter => service.deleteWhere(Project)(newFilter && createdAtFilter))

    val schedule: ZSchedule[Clock, Any, Int] = ZSchedule
      .fixed(interval)
      .logInput(_ => UIO(Logger.debug(s"Deleting draft projects")))

    val action = (updateFalseNewProjects *> deleteNewProjects).unit.delay(interval)

    val task = action.option.unit.repeat(schedule).fork

    ZManaged
      .make(
        UIO(Logger.info("ProjectTask starting")) *> task <* UIO(
          Logger.info(s"ProjectTask started. First run in ${interval.asScala.toSeconds} seconds.")
        )
      )(
        fiber => fiber.interrupt
      )
      .unit
  }
}
