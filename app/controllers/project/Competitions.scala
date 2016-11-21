package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import db.ModelService
import form.OreForms
import form.project.CompetitionData
import models.project.Competition
import ore.permission.EditCompetitions
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.StringUtils
import views.{html => views}

import cats.effect.IO
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
        competitions
          .exists(StringUtils.equalsIgnoreCase(_.name, request.body.name))
          .ifM(
            IO.pure(Redirect(self.showCreator()).withError("error.unique.competition.name")),
            competitions.add(Competition.partial(request.user, request.body)).as(Redirect(self.showManager()))
          )
    }
}
