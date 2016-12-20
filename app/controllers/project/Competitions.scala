package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import db.{DbRef, ModelService}
import db.impl.OrePostgresDriver.api._
import form.OreForms
import form.project.competition.{CompetitionCreateForm, CompetitionSaveForm}
import models.competition.Competition
import ore.permission.EditCompetitions
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.StringUtils
import views.{html => views}

import cats.effect.IO
import cats.syntax.all._

/**
  * Handles competition based actions.
  */
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

  private val self = routes.Competitions

  private def EditCompetitionsAction: ActionBuilder[Requests.AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(EditCompetitions))

  /**
    * Shows the competition administrative panel.
    *
    * @return Competition manager
    */
  def showManager(): Action[AnyContent] = EditCompetitionsAction.asyncF { implicit request =>
    competitions.sorted(_.createdAt).map(all => Ok(views.projects.competitions.manage(all)))
  }

  /**
    * Shows the competition creator.
    *
    * @return Competition creator
    */
  def showCreator(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.create())
  }

  /**
    * Creates a new competition.
    *
    * @return Redirect to manager or creator with errors.
    */
  def create(): Action[CompetitionCreateForm] =
    EditCompetitionsAction(parse.form(forms.CompetitionCreate, onErrors = FormError(self.showCreator()))).asyncF {
      implicit request =>
        competitions
          .exists(StringUtils.equalsIgnoreCase(_.name, request.body.name))
          .ifM(
            IO.pure(Redirect(self.showCreator()).withError("error.unique.competition.name")),
            competitions.add(Competition.partial(request.user, request.body)).as(Redirect(self.showManager()))
          )
    }

  /**
    * Saves the competition with the specified ID.
    *
    * @param id Competition ID
    * @return   Redirect to manager
    */
  def save(id: DbRef[Competition]): Action[CompetitionSaveForm] =
    EditCompetitionsAction
      .andThen(AuthedCompetitionAction(id))(parse.form(forms.CompetitionSave, onErrors = FormError(self.showManager())))
      .asyncEitherT { implicit request =>
        competitions.get(id).toRight(notFound).semiflatMap { competition =>
          competition.save(request.body).as(Redirect(self.showManager()).withSuccess("success.saved.competition"))
        }
      }

  /**
    * Deletes the competition with the specified ID.
    *
    * @param id Competition ID
    * @return   Redirect to manager
    */
  def delete(id: DbRef[Competition]): Action[AnyContent] = EditCompetitionsAction.andThen(AuthedCompetitionAction(id)).asyncF { implicit request =>
    this.competitions
      .remove(request.competition)
      .as(Redirect(self.showManager()).withSuccess("success.deleted.competition"))
  }
}
