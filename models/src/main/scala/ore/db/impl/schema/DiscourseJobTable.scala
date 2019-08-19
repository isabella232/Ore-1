package ore.db.impl.schema

import java.time.LocalDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.discourse.DiscourseJob
import ore.models.project.{Project, Version}

class DiscourseJobTable(tag: Tag) extends ModelTable[DiscourseJob](tag, "discourse_job") {

  def projectId   = column[DbRef[Project]]("project_id")
  def versionId   = column[DbRef[Version]]("version_id")
  def topicId     = column[Int]("topic_id")
  def poster      = column[String]("poster")
  def jobType     = column[DiscourseJob.JobType]("job_type")
  def retryIn     = column[LocalDateTime]("retry_in")
  def attempts    = column[Int]("attempts")
  def lastRequest = column[LocalDateTime]("last_request")
  def visibility  = column[Boolean]("visibility")

  override def * =
    (
      id.?,
      createdAt.?,
      (projectId.?, versionId.?, topicId.?, poster.?, jobType, retryIn.?, attempts, lastRequest, visibility.?)
    ) <> (mkApply(
      (DiscourseJob.apply _).tupled
    ), mkUnapply(DiscourseJob.unapply))
}
