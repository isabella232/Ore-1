package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, AnyContent}

import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.OreRequest
import db.ModelService
import ore.permission._
import ore.project.factory.creation.ProjectCreationFactory
import ore.project.io.{PluginUpload, ProjectFiles}
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.{projects => views}

/**
  * Controller for handling Project related actions.
  */
class VersionCreation @Inject()(creationFactory: ProjectCreationFactory)(
    implicit val ec: ExecutionContext,
    cache: AsyncCacheApi,
    bakery: Bakery,
    sso: SingleSignOnConsumer,
    auth: SpongeAuthApi,
    env: OreEnv,
    config: OreConfig,
    service: ModelService
) extends OreBaseController {

  implicit val fileManager: ProjectFiles = creationFactory.fileManager
  private val self                       = controllers.project.routes.VersionCreation

  /**
    * Displays the "create version" page.
    * Only accessible when the user account is not locked and has access to upload files to the project.
    * Gives immediate feedback if users PGP Key is not valid
    *
    * @return Create version view
    */
  def showStep1(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).asyncF { request =>
    implicit val r: OreRequest[AnyContent] = request.request
    val user                               = request.user

    user.isPgpPubKeyReadyForUpload.value.map { pgpValid =>
      Ok(views.versions.creation.step1(pgpValid, author, slug))
    }
  }

  /**
    *
    * @return
    */
  def processStep1(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).asyncF {
    request =>
      implicit val r: OreRequest[AnyContent] = request.request
      val user                               = request.user

      user.isPgpPubKeyReadyForUpload.value.map {
        case Left(error) => Redirect(self.showStep1(author, slug)).withError(error)
        case Right(()) =>
          val uploadData = PluginUpload.bindFromRequestArray()

          if (uploadData.isEmpty) {
          // No data found
            Redirect(self.showStep1(author, slug)).withError("error.noFile")

          } else {
            // Process the uploads
            Redirect("")
      }
  }
  }

  /*
  def upload(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).async { implicit request =>

    EitherT.fromEither[Future](uploadData).flatMap { data =>
      //TODO: We should get rid of this try
      try {
        this.factory.processSubsequentPluginUpload(data, user, request.data.project).leftMap(err => Redirect(call).withError(err))
      } catch {
        case e: InvalidPluginFileException =>
          EitherT.leftT[Future, PendingVersion](Redirect(call).withErrors(Option(e.getMessage).toList))
      }
    }.map { pendingVersion =>
      pendingVersion.copy(underlying = pendingVersion.underlying.copy(authorId = user.id.value)).cache()
      Redirect(self.showCreatorWithMeta(request.data.project.ownerName, slug, pendingVersion.underlying.versionString))
    }.merge
  }
   */

  /**
    * Will check if user is unlocked, has access to upload version and give user and project back in the request
    */
  private def VersionUploadAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(UploadVersions))
}
