package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.OreBaseController
import db.ModelService
import form.OreForms
import models.project.Competition
import ore.{OreConfig, OreEnv}
import ore.permission.EditCompetitions
import controllers.sugar.{Bakery, Requests}
import form.project.CompetitionData
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.syntax.all._

class Competitions @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    env: OreEnv,
    messagesApi: MessagesApi,
    config: OreConfig,
    service: ModelService,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    cache: AsyncCacheApi
) extends OreBaseController {
  identity(messagesApi)

  private val self         = routes.Competitions
  private val competitions = this.service.access[Competition]()

  def EditCompetitionsAction: ActionBuilder[Requests.AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(EditCompetitions))

  def showManager(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.manage())
  }

  def showCreator(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.create())
  }

  def create(): Action[CompetitionData] =
    EditCompetitionsAction(parse.form(forms.CompetitionCreate, onErrors = FormError(self.showCreator()))).asyncF {
      implicit request =>
        this.competitions.add(Competition.partial(request.user, request.body)).as(Redirect(self.showManager()))
    }
}
