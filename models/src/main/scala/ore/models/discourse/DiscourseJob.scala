package ore.models.discourse

import java.time.LocalDateTime

import scala.collection.immutable

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.DiscourseJobTable
import ore.db.{DbRef, ModelQuery}
import ore.models.project.{Project, Version}

import enumeratum.values.{IntEnum, IntEnumEntry}
import slick.lifted.TableQuery

case class DiscourseJob(
    projectId: Option[DbRef[Project]] = None,
    versionId: Option[DbRef[Version]] = None,
    topicId: Option[Int] = None,
    poster: Option[String] = None,
    jobType: DiscourseJob.JobType,
    retryIn: Option[LocalDateTime] = None,
    attempts: Int = 0,
    lastRequested: LocalDateTime,
    visibility: Option[Boolean] = None
)
object DiscourseJob extends DefaultModelCompanion[DiscourseJob, DiscourseJobTable](TableQuery[DiscourseJobTable]) {

  implicit val query: ModelQuery[DiscourseJob] =
    ModelQuery.from(this)

  sealed abstract class JobType(val value: Int) extends IntEnumEntry
  object JobType extends IntEnum[JobType] {

    override def values: immutable.IndexedSeq[JobType] = findValues

    case object CreateTopic       extends JobType(0)
    case object UpdateTopic       extends JobType(1)
    case object CreateVersionPost extends JobType(2)
    case object UpdateVersionPost extends JobType(3)
    case object SetVisibility     extends JobType(4)
    case object DeleteTopic       extends JobType(5)
  }
}
