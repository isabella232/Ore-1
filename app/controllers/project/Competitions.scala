package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.OreBaseController
import db.ModelService
import ore.{OreConfig, OreEnv}
import ore.permission.EditCompetitions
import controllers.sugar.{Bakery, Requests}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

class Competitions @Inject()(
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

  def EditCompetitionsAction: ActionBuilder[Requests.AuthRequest, AnyContent] = Authenticated.andThen(PermissionAction(EditCompetitions))

  def showManager(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.manage())
  }

  def showCreator(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.create())
  }

}
