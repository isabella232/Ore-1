package discourse

import scala.language.higherKinds

import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import ore.OreConfig
import ore.db.ModelService
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.DiscourseJobTable
import ore.models.discourse.DiscourseJob
import ore.models.discourse.DiscourseJob.JobType
import ore.models.project.{Project, Version}
import util.syntax._

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{UIO, ZIO, ZManaged, ZSchedule}

/**
  * Task to periodically retry failed Discourse requests.
  */
object RecoveryTask {

  def program(config: OreConfig, api: OreDiscourseApi[UIO])(
      implicit service: ModelService[UIO]
  ): ZManaged[Clock, Nothing, Unit] = {
    val Logger: scalalogging.Logger = scalalogging.Logger("Discourse")

    val nextJobs = ZIO
      .accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS))
      .map(Instant.ofEpochMilli)
      .map(LocalDateTime.ofInstant(_, ZoneId.of("UTC")))
      .flatMap(now => service.runDBIO(TableQuery[DiscourseJobTable].filter(j => j.retryIn < now).take(10).result))

    val taskContent: ZIO[Clock, Nothing, Unit] = {
      val logStart = UIO(Logger.debug("Running Discourse recovery task..."))

      val doWork: ZIO[Clock, Nothing, Unit] = nextJobs
        .flatMap { seq =>
          ZIO.foreachPar(seq) { job =>
            val jobProject = ZIO.fromOption(job.projectId).flatMap(ModelView.now(Project).get(_).toZIO)
            val jobVersion = ZIO.fromOption(job.versionId).flatMap(ModelView.now(Version).get(_).toZIO)

            val jobProjectVersion = jobProject <*> jobVersion

            val objId = job.jobType match {
              case JobType.CreateTopic => jobProject.flatMap(api.createProjectTopic).const(job.id)
              case JobType.UpdateTopic => jobProject.flatMap(api.updateProjectTopic).const(job.id)
              case JobType.CreateVersionPost =>
                jobProjectVersion.flatMap(Function.tupled(api.createVersionPost)).const(job.id)
              case JobType.UpdateVersionPost =>
                jobProjectVersion.flatMap(Function.tupled(api.updateVersionPost)).const(job.id)
              case JobType.SetVisibility =>
                (jobProject.map(_.obj) <*> ZIO.fromOption(job.visibility))
                  .map(Function.tupled(api.changeTopicVisibility))
                  .const(job.id)
              case JobType.DeleteTopic => jobProject.flatMap(api.deleteProjectTopic).const(job.id)
            }

            objId.option.flatMap {
              case Some(id) => ZIO.succeed(id.value)
              case None =>
                ZIO
                  .effectTotal(Logger.warn(s"Found discourse job with missing values, ignoring: $job"))
                  .const(job.id.value)
            }
          }
        }
        .flatMap(doneJobs => service.deleteWhere(DiscourseJob)(_.id.inSetBind(doneJobs)))
        .unit

      val logDone = UIO(Logger.debug("Done"))

      logStart *> doWork *> logDone
    }

    val retryRate = config.forums.retryRate

    val interval: zio.duration.Duration = zio.duration.Duration.fromScala(retryRate)

    val schedule: ZSchedule[Clock, Any, Int] = ZSchedule.fixed(interval)

    val task = taskContent.option.unit.repeat(schedule).fork

    ZManaged
      .make(
        UIO(Logger.info("RecoveryTask starting")) *> task <* UIO(
          Logger.info(s"Discourse recovery task started. First run in ${retryRate.toSeconds} seconds.")
        )
      )(
        fiber => fiber.interrupt
      )
      .unit
  }

}
