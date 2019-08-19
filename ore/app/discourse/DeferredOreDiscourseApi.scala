package discourse

import scala.language.higherKinds

import java.time.LocalDateTime

import ore.db.{Model, ModelService}
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.DiscourseJobTable
import ore.discourse.DiscoursePost
import ore.models.discourse.DiscourseJob
import ore.models.project.{Project, Version}
import ore.models.user.User

import cats.Monad
import cats.syntax.all._
import slick.lifted.TableQuery

class DeferredOreDiscourseApi[F[_]](underlying: OreDiscourseApi[F])(implicit F: Monad[F], service: ModelService[F])
    extends OreDiscourseApi[F] {

  override def createProjectTopic(project: Model[Project]): F[Model[Project]] =
    service
      .insert(
        DiscourseJob(
          projectId = Some(project.id),
          jobType = DiscourseJob.JobType.CreateTopic,
          lastRequested = LocalDateTime.now()
        )
      )
      .as(project)

  override def updateProjectTopic(project: Model[Project]): F[Boolean] =
    service
      .runDBIO(
        TableQuery[DiscourseJobTable]
          .filter(
            j =>
              j.projectId === project.id.value && j.jobType === (DiscourseJob.JobType.UpdateTopic: DiscourseJob.JobType)
          )
          .result
          .headOption
      )
      .flatMap {
        case None =>
          service.insert(
            DiscourseJob(
              projectId = Some(project.id),
              jobType = DiscourseJob.JobType.CreateTopic,
              lastRequested = LocalDateTime.now()
            )
          )
        case Some(job) => service.update(job)(_.copy(lastRequested = LocalDateTime.now()))
      }
      .as(true)

  override def postDiscussionReply(project: Project, user: User, content: String): F[DiscoursePost] =
    underlying.postDiscussionReply(project, user, content)

  override def createVersionPost(project: Model[Project], version: Model[Version]): F[Model[Version]] =
    service
      .insert(
        DiscourseJob(
          projectId = Some(project.id),
          versionId = Some(version.id),
          jobType = DiscourseJob.JobType.CreateVersionPost,
          lastRequested = LocalDateTime.now()
        )
      )
      .as(version)

  override def updateVersionPost(project: Model[Project], version: Model[Version]): F[Boolean] =
    service
      .runDBIO(
        TableQuery[DiscourseJobTable]
          .filter { j =>
            j.projectId === project.id.value && j.versionId === version.id.value && j.jobType === (DiscourseJob.JobType.UpdateVersionPost: DiscourseJob.JobType)
          }
          .result
          .headOption
      )
      .flatMap {
        case None =>
          service.insert(
            DiscourseJob(
              projectId = Some(project.id),
              versionId = Some(version.id),
              jobType = DiscourseJob.JobType.UpdateVersionPost,
              lastRequested = LocalDateTime.now()
            )
          )
        case Some(job) => service.update(job)(_.copy(lastRequested = LocalDateTime.now()))
      }
      .as(true)

  override def changeTopicVisibility(project: Project, isVisible: Boolean): F[Unit] =
    service
      .runDBIO(
        TableQuery[DiscourseJobTable]
          .filter { j =>
            j.topicId === project.topicId && j.jobType === (DiscourseJob.JobType.SetVisibility: DiscourseJob.JobType)
          }
          .result
          .headOption
      )
      .flatMap {
        case None =>
          service.insert(
            DiscourseJob(
              topicId = project.topicId,
              jobType = DiscourseJob.JobType.SetVisibility,
              lastRequested = LocalDateTime.now(),
              visibility = Some(isVisible)
            )
          )
        case Some(job) => service.update(job)(_.copy(lastRequested = LocalDateTime.now(), visibility = Some(isVisible)))
      }
      .void

  override def deleteProjectTopic(project: Model[Project]): F[Model[Project]] =
    service
      .runDBIO(
        TableQuery[DiscourseJobTable]
          .filter { j =>
            j.projectId === project.id.value && j.jobType === (DiscourseJob.JobType.DeleteTopic: DiscourseJob.JobType)
          }
          .result
          .headOption
      )
      .flatMap {
        case None =>
          service.insert(
            DiscourseJob(
              projectId = Some(project.id),
              jobType = DiscourseJob.JobType.DeleteTopic,
              lastRequested = LocalDateTime.now()
            )
          )
        case Some(job) => service.update(job)(_.copy(lastRequested = LocalDateTime.now()))
      }
      .as(project)

  override def isAvailable: F[Boolean] = underlying.isAvailable
}
