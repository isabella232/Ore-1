package ore.models.project

import java.time.OffsetDateTime

import ore.data.project.Dependency
import ore.db.access.{ModelView, QueryView}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.{Describable, Hideable}
import ore.db.impl.schema._
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.admin.{Review, VersionVisibilityChange}
import ore.models.user.User
import ore.syntax._
import ore.util.FileUtils

import cats.data.OptionT
import cats.syntax.all._
import cats.{Monad, Parallel}
import enumeratum.values._
import io.circe._
import io.circe.syntax._
import slick.lifted.TableQuery

/**
  * Represents a single version of a Project.
  *
  * @param description     User description of version
  * @param projectId        ID of project this version belongs to
  */
case class Version(
    projectId: DbRef[Project],
    name: String,
    slug: String,
    authorId: Option[DbRef[User]],
    description: Option[String],
    reviewState: ReviewState = ReviewState.Unreviewed,
    reviewerId: Option[DbRef[User]] = None,
    approvedAt: Option[OffsetDateTime] = None,
    visibility: Visibility = Visibility.Public,
    createForumPost: Boolean = true,
    postId: Option[Int] = None,
    pluginAssetId: DbRef[Asset],
    docsAssetId: Option[DbRef[Asset]] = None,
    sourcesAssetId: Option[DbRef[Asset]] = None,
    tags: Version.VersionTags
) extends Describable {

  //TODO: Check this in some way
  //checkArgument(description.exists(_.length <= Page.maxLength), "content too long", "")

  /**
    * Returns the base URL for this Version.
    *
    * @return Base URL for version
    */
  def url(project: Project): String = project.url + "/versions/" + this.slug

  def reviewer[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, UserTable, Model[User]]): Option[QOptRet] =
    this.reviewerId.map(view.get)

  def reviewById[F[_]](id: DbRef[Review])(implicit service: ModelService[F]): OptionT[F, Model[Review]] =
    ModelView.now(Review).get(id)
}

object Version extends DefaultModelCompanion[Version, VersionTable](TableQuery[VersionTable]) {

  case class VersionTags(
      usesMixin: Boolean,
      stability: Stability,
      releaseType: Option[ReleaseType],
      channelName: Option[String] = None,
      channelColor: Option[TagColor] = None
  )

  sealed abstract class Stability(val value: String) extends StringEnumEntry
  object Stability extends StringEnum[Stability] {
    override def values: IndexedSeq[Stability] = findValues

    case object Recommended extends Stability("recommended")
    case object Stable      extends Stability("stable")
    case object Beta        extends Stability("beta")
    case object Alpha       extends Stability("alpha")
    case object Bleeding    extends Stability("bleeding")
    case object Unsupported extends Stability("unsupported")
    case object Broken      extends Stability("broken")

    implicit val codec: Codec[Stability] = Codec.from(
      (c: HCursor) =>
        c.as[String]
          .flatMap { str =>
            withValueOpt(str).toRight(io.circe.DecodingFailure.apply(s"$str is not a valid stability", c.history))
          },
      (a: Stability) => a.value.asJson
    )
  }

  sealed abstract class ReleaseType(val value: String) extends StringEnumEntry
  object ReleaseType extends StringEnum[ReleaseType] {
    override def values: IndexedSeq[ReleaseType] = findValues

    case object MajorUpdate extends ReleaseType("major_update")
    case object MinorUpdate extends ReleaseType("minor_update")
    case object Patches     extends ReleaseType("patches")
    case object Hotfix      extends ReleaseType("hotfix")

    implicit val codec: Codec[ReleaseType] = Codec.from(
      (c: HCursor) =>
        c.as[String]
          .flatMap { str =>
            withValueOpt(str).toRight(io.circe.DecodingFailure.apply(s"$str is not a valid release type", c.history))
          },
      (a: ReleaseType) => a.value.asJson
    )
  }

  implicit val query: ModelQuery[Version] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[Version] = (a: Version) => a.projectId

  implicit def versionIsHideable[F[_]](
      implicit service: ModelService[F],
      F: Monad[F],
      par: Parallel[F]
  ): Hideable.Aux[F, Version, VersionVisibilityChange, VersionVisibilityChangeTable] = new Hideable[F, Version] {
    override type MVisibilityChange      = VersionVisibilityChange
    override type MVisibilityChangeTable = VersionVisibilityChangeTable

    override def visibility(m: Version): Visibility = m.visibility

    override def setVisibility(m: Model[Version])(
        visibility: Visibility,
        comment: String,
        creator: DbRef[User]
    ): F[(Model[Version], Model[VersionVisibilityChange])] = {
      val updateOldChange = lastVisibilityChange(m)(ModelView.now(VersionVisibilityChange))
        .semiflatMap { vc =>
          service.update(vc)(
            _.copy(
              resolvedAt = Some(OffsetDateTime.now()),
              resolvedBy = Some(creator)
            )
          )
        }
        .cata((), _ => ())

      val createNewChange = service.insert(
        VersionVisibilityChange(
          Some(creator),
          m.id,
          comment,
          None,
          None,
          visibility
        )
      )

      val updateVersion = service.update(m)(
        _.copy(
          visibility = visibility
        )
      )

      updateOldChange *> (updateVersion, createNewChange).parTupled
    }

    override def visibilityChanges[V[_, _]: QueryView](m: Model[Version])(
        view: V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]]
    ): V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]] = view.filterView(_.versionId === m.id.value)
  }

  implicit class VersionModelOps(private val self: Model[Version]) extends AnyVal {

    def reviewEntries[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      view.filterView(_.versionId === self.id.value)

    def unfinishedReviews[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      reviewEntries(view).sortView(_.createdAt).filterView(_.endedAt.?.isEmpty)

    def mostRecentUnfinishedReview[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, ReviewTable, Model[Review]]
    ): QOptRet =
      unfinishedReviews(view).one

    def mostRecentReviews[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      reviewEntries(view).sortView(_.createdAt)
  }
}
