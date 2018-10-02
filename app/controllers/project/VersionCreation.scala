package controllers.project

import cats.instances.future._
import cats.syntax.all._
import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import controllers.sugar.Requests.{AuthRequest, OreRequest}
import db.ModelService
import db.impl.OrePostgresDriver.api._
import discourse.OreDiscourseApi
import form.OreForms
import javax.inject.Inject
import models.project.{Tag, TagColor, Version}
import models.user.{LoggedAction, UserActionLogger}
import models.viewhelper.OrganizationData
import ore.permission._
import ore.permission.role.RoleType
import ore.project.factory.ProjectFactory
import ore.project.factory.TagAlias.ProjectTag
import ore.project.factory.creation.{PendingProjectCreation, ProjectCreationFactory}
import ore.project.io.{PluginUpload, ProjectFiles}
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.{AsyncCacheApi, SyncCacheApi}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.{projects => views}

import scala.concurrent.{ExecutionContext, Future}

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
  def showStep1(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).async { request =>
    implicit val r: OreRequest[AnyContent] = request.request
    val user                               = request.user
    for {
      pgpValid <- user.isPgpPubKeyReadyForUpload
    } yield {
      Ok(views.versions.creation.step1(pgpValid, author, slug))
    }
  }

  /**
    *
    * @return
    */
  def processStep1(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).async {
    request =>
      implicit val r: OreRequest[AnyContent] = request.request
      val user                               = request.user

      for {
        // PGP Validation check
        pgpValid <- user.isPgpPubKeyReadyForUpload
      } yield {

        // Start validation process
        if (!pgpValid._1) {
          // Show error from PGP Key
          Redirect(self.showStep1(author, slug)).withError(pgpValid._2)
        } else {
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
