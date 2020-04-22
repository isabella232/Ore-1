package models.viewhelper

import scala.language.higherKinds

import controllers.sugar.Requests.ProjectRequest
import ore.db.access.ModelView
import ore.db.{Model, ModelService}
import ore.models.project.Version
import ore.models.user.User

import cats.syntax.all._
import cats.{MonadError, Parallel}

case class VersionData(
    p: ProjectData,
    v: Model[Version],
    approvedBy: Option[String] // Reviewer if present
) {

  def fullSlug = s"""${p.fullSlug}/versions/${v.slug}"""
}

object VersionData {
  def of[F[_]](request: ProjectRequest[_], version: Model[Version])(
      implicit service: ModelService[F],
      F: MonadError[F, Throwable],
      par: Parallel[F]
  ): F[VersionData] = {
    import cats.instances.option._

    version
      .reviewer(ModelView.now(User))
      .sequence
      .subflatMap(identity)
      .map(_.name)
      .value
      .map(approvedBy => VersionData(request.data, version, approvedBy))
  }
}
