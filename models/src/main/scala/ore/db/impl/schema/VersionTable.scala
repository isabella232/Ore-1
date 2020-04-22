package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.{DescriptionColumn, VisibilityColumn}
import ore.models.project.{Project, ReviewState, TagColor, Version}
import ore.models.user.User

//noinspection MutatorLikeMethodIsParameterless
class VersionTable(tag: Tag)
    extends ModelTable[Version](tag, "project_versions")
    with DescriptionColumn[Version]
    with VisibilityColumn[Version] {

  def name            = column[String]("name")
  def slug            = column[String]("slug")
  def projectId       = column[DbRef[Project]]("project_id")
  def authorId        = column[DbRef[User]]("author_id")
  def reviewStatus    = column[ReviewState]("review_state")
  def reviewerId      = column[DbRef[User]]("reviewer_id")
  def approvedAt      = column[OffsetDateTime]("approved_at")
  def createForumPost = column[Boolean]("create_forum_post")
  def postId          = column[Option[Int]]("post_id")

  def stability    = column[Version.Stability]("stability")
  def releaseType  = column[Version.ReleaseType]("release_type")
  def channelName  = column[String]("legacy_channel_name")
  def channelColor = column[TagColor]("legacy_channel_color")

  def tags =
    (
      stability,
      releaseType.?,
      channelName.?,
      channelColor.?
    ) <> (Version.VersionTags.tupled, Version.VersionTags.unapply)

  override def * = {
    (
      id.?,
      createdAt.?,
      (
        projectId,
        name,
        slug,
        authorId.?,
        description.?,
        reviewStatus,
        reviewerId.?,
        approvedAt.?,
        visibility,
        createForumPost,
        postId,
        tags
      )
    ) <> (mkApply((Version.apply _).tupled), mkUnapply(Version.unapply))
  }
}
