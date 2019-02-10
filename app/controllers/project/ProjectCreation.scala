package controllers.project

import cats.instances.vector._
import cats.syntax.all._
import controllers.OreBaseController
import controllers.sugar.Bakery
import db.ModelService
import form.OreForms
import javax.inject.Inject
import ore.permission._
import ore.project.io.PluginUpload
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, AnyContent}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.{projects => views}

import scala.concurrent.ExecutionContext

class ProjectCreation @Inject()(forms: OreForms, stats: StatTracker)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    cache: AsyncCacheApi,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    config: OreConfig,
    service: ModelService,
    auth: SpongeAuthApi,
) extends OreBaseController {

  private val self = controllers.project.routes.ProjectCreation

  /**
    * Displays the "create project" page.
    * Only accessible when the user account is not locked.
    * Gives immediate feedback if users PGP Key is not valid
    * Gives the choose to whom to upload
    *
    * @return Create project view
    */
  def showStep1(): Action[AnyContent] = UserLock().asyncF { implicit request =>
    val user = request.user
    for {
      pgpValid   <- user.isPgpPubKeyReadyForUpload()
      orgas      <- request.user.organizations.allFromParent(request.user)
      createOrga <- orgas.toVector.parTraverse(request.user.can(CreateProject).in(_))
    } yield {
      val createdOrgas = orgas.zip(createOrga).collect {
        case (orga, true) => orga
      }
      Ok(views.creation.step1(pgpValid, createdOrgas))
    }
  }

  /**
    * Process the post from Step 1
    * Will check if user is not locked
    * Will validate the PGP Key and the state of it
    * Gets a list of all the organization the user can upload to
    * Get uploadData (file & sig)
    *
    * @return
    */
  def processStep1(): Action[AnyContent] = UserLock().asyncF { implicit request =>
    val user = request.user
    for {
      // PGP Validation check
      pgpValid <- user.isPgpPubKeyReadyForUpload

      // Get organization the user is allowed to upload to
      orgas     <- request.user.organizations.allFromParent(request.user)
      canCreate <- orgas.toVector.parTraverse(org => user.can(CreateProject).in(org).tupleLeft(org.id.value))
    } yield {
      val others = canCreate.collect {
        case (id, true) => id
      }

      val canUploadTo = others.toSet + user.id.value // Add self

      // Start validation process
      if (!pgpValid._1) {
        // Show error from PGP Key
        Redirect(self.showStep1()).withError(pgpValid._2)

      } else {
        val uploadData = PluginUpload.bindFromRequest()

        if (uploadData.isEmpty) {
          // No data found
          Redirect(self.showStep1()).withError("error.noFile")

        } else {
          // Process the form (returns the optional selected owner)
          this.forms
            .ProjectCreateStep1(canUploadTo.toSeq)
            .bindFromRequest()
            .fold(
              hasErrors => Redirect(self.showStep1()).withError("error.plugin.owner"),
              formData => {
                // Get selected project Owner (fallback to user itself)
                val projectOwner = formData.getOrElse(user.id.value)

                Redirect(self.showStep1()).withError("general.appName")
                /*
              // Peding project can throw Exceptions so we will give it a try catch block
              try {
                val pendingProjectCreate = this.creationFactory.createProjectStep1(uploadData.get, user, projectOwner)

                // Project can return errors so check if we have them
                pendingProjectCreate match {
                  case Right(pendingProject) =>
                    // Cache project
                    pendingProject.cache()

                    // Show step2
                    Redirect(self.showStep2()).withCookies(bakery.bake("_newproject", pendingProject.key))

                  case Left(errorMessage) =>
                    Redirect(self.showStep1()).withError(errorMessage)
                }
              } catch {
                case e: Exception =>
                  Redirect(self.showStep1()).withErrors(Option(e.getMessage).toList)
              }*/
              }
            )
        }
      }
    }
  }
}
