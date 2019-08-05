package discourse

import scala.language.higherKinds

import ore.OreConfig
import ore.db.ModelService
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTableMain, VersionTable}
import ore.models.project.{Project, Version, Visibility}

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{Task, UIO, ZIO, ZManaged, ZSchedule}

/**
  * Task to periodically retry failed Discourse requests.
  */
object RecoveryTask {

  def program(config: OreConfig, api: OreDiscourseApi[Task])(
      implicit service: ModelService[Task]
  ): ZManaged[Clock, Nothing, Unit] = {
    val Logger: scalalogging.Logger = scalalogging.Logger("Discourse")

    val projectTopicFilter = ModelFilter(Project)(_.topicId.isEmpty)
    val projectDirtyFilter = ModelFilter(Project)(_.isTopicDirty)
    val visibleFilter      = Visibility.isPublicFilter[ProjectTableMain]

    val toCreateProjects   = ModelView.raw(Project).filter(projectTopicFilter && visibleFilter).to[Vector]
    val dirtyTopicProjects = ModelView.raw(Project).filter(projectDirtyFilter && visibleFilter).to[Vector]

    val versionsQueryBase = for {
      (version, project) <- TableQuery[VersionTable].join(TableQuery[ProjectTableMain]).on(_.projectId === _.id)
      if version.createForumPost
      if visibleFilter(project)
    } yield (project, version)

    val versionTopicFilter = ModelFilter(Version)(_.postId.isEmpty)
    val versionDirtyFilter = ModelFilter(Version)(_.isPostDirty)

    val toCreateVersions  = versionsQueryBase.filter(v => versionTopicFilter(v._2)).to[Vector]
    val dirtyPostVersions = versionsQueryBase.filter(v => versionDirtyFilter(v._2)).to[Vector]

    val taskContent: Task[Unit] = {
      val logStart = UIO(Logger.debug("Running Discourse recovery task..."))

      def runUpdates[T, M, A](query: Query[T, M, Vector], error: String)(
          logSize: Int => String
      )(use: Vector[M] => Task[A]): Task[A] =
        service
          .runDBIO(query.result)
          .flatMap(models => UIO(Logger.debug(logSize(models.size))) *> use(models))

      val createProjects = runUpdates(toCreateProjects, "Failed to create project topic")(
        size => s"Creating $size topics..."
      )(toCreate => ZIO.foreach_(toCreate)(api.createProjectTopic).option)

      val updateProjects = runUpdates(dirtyTopicProjects, "Failed to update dirty project")(
        size => s"Updating $size topics..."
      )(toUpdate => ZIO.foreach_(toUpdate)(api.updateProjectTopic).option)

      val createVersions = runUpdates(toCreateVersions, "Failed to create version post")(
        size => s"Creating $size posts..."
      )(toCreate => ZIO.foreach_(toCreate)(t => api.createVersionPost(t._1, t._2).option))

      val updateVersions = runUpdates(dirtyPostVersions, "Failed to update dirty version")(
        size => s"Updating $size posts..."
      )(toUpdate => ZIO.foreach_(toUpdate)(t => api.updateVersionPost(t._1, t._2).option))

      val logDone = UIO(Logger.debug("Done"))
      // TODO: We need to keep deleted projects in case the topic cannot be deleted

      val doWork: Task[Unit] = createProjects &> updateProjects &> createVersions &> updateVersions

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
