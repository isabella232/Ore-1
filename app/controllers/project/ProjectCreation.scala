package controllers.project

import cats.data.OptionT
import cats.instances.future._
import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.ModelService
import discourse.OreDiscourseApi
import form.OreForms
import javax.inject.Inject
import models.viewhelper.OrganizationData
import ore.permission._
import ore.project.factory.ProjectFactory
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
class ProjectCreation @Inject()(stats: StatTracker,
                                forms: OreForms,
                                factory: ProjectFactory,
                                creationFactory: ProjectCreationFactory,
                                implicit val syncCache: SyncCacheApi,
                                implicit override val cache: AsyncCacheApi,
                                implicit override val bakery: Bakery,
                                implicit override val sso: SingleSignOnConsumer,
                                implicit val forums: OreDiscourseApi,
                                implicit override val messagesApi: MessagesApi,
                                implicit override val env: OreEnv,
                                implicit override val config: OreConfig,
                                implicit override val service: ModelService,
                                implicit override val auth: SpongeAuthApi)(implicit val ec: ExecutionContext)
                         extends OreBaseController {

  implicit val fileManager: ProjectFiles = creationFactory.fileManager
  private val self = controllers.project.routes.ProjectCreation

  /**
    * Displays the "create project" page.
    * Only accessible when the user account is not locked.
    * Gives immediate feedback if users PGP Key is not valid
    * Gives the choose to whom to upload
    *
    * @return Create project view
    */
  def showStep1(): Action[AnyContent] = UserLock() async { implicit request =>
    val user = request.user
    for {
      pgpValid <- user.isPgpPubKeyReadyForUpload
      userOrgas <- user.organizations.all
      userOrgasCanCreate <- Future.traverse(userOrgas)(org => user can CreateProject in org map { perm => (org, perm)})
    } yield {
      val createProjectOrgas = userOrgasCanCreate.collect {
        case (orga, perm) if perm => orga
      }

      Ok(views.creation.step1(pgpValid, createProjectOrgas.toSeq))
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
  def processStep1(): Action[AnyContent] = UserLock() async { implicit request =>
    val user = request.user
    for {
      // PGP Validation check
      pgpValid <- user.isPgpPubKeyReadyForUpload

      // Get organization the user is allowed to upload to
      userOrgas <- user.organizations.all
      userOrgasCanCreate <- Future.traverse(userOrgas)(org => user can CreateProject in org map { perm => (org.id.value, perm)})

    } yield {
      // Make list of userId's current user can upload to
      var canUploadTo = userOrgasCanCreate.collect {
        case (orgaId, perm) if perm => orgaId
      }
      canUploadTo = canUploadTo + user.id.value

      // Start validation process
      if (pgpValid._1 == false) {
        // Show error from PGP Key
        Redirect(self.showStep1()).withError(pgpValid._2)

      } else {
        val uploadData = PluginUpload.bindFromRequest()

        if (uploadData.isEmpty) {
          // No data found
          Redirect(self.showStep1()).withError("error.noFile")

        } else {
          // Process the form (returns the optional selected owner)
          this.forms.ProjectCreateStep1(canUploadTo.toSeq).bindFromRequest().fold(
            hasErrors =>
              // Show error
              Redirect(self.showStep1()).withError(pgpValid._2),

            formData => {
              // Get selected project Owner (fallback to user itself)
              val projectOwner = formData.getOrElse(user.id.value)

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
              }
            }
          )
        }
      }
    }
  }


  /**
    * Displays the metadata of the uploaded file in Step1
    * Validates the availability of the ID and Name
    * Shows basic configuration options
    *
    * @return Configuration view
    */
  def showStep2(): Action[AnyContent] = UserLock() async { implicit request =>
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {

        // Check namespaces
        for {
          pluginId <- this.projects.withPluginId(pendingProject.underlying.pluginId).value
          namespaceAvailable <- this.projects.isNamespaceAvailable(pendingProject.underlying.ownerName, pendingProject.underlying.slug)
        } yield {
          val pluginIdAvailable = pluginId.isEmpty

          var errors: Set[String] = Set.empty
          if (!pluginIdAvailable) errors += "error.noFile"
          if (!namespaceAvailable) errors += "error.noNamespace"
          //TODO: Show errors correctly
          //if (errors.nonEmpty) Redirect(self.showStep2()).withErrors(errors.toSeq)

          Ok(views.creation.step2(pendingProject, pluginIdAvailable, namespaceAvailable))
        }
      }
    }
  }

  /**
    * Process the post from Step 2
    *
    * @return
    */
  def processStep2(): Action[AnyContent] = UserLock() async { implicit request =>
    // Get the possible pending project
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {
        this.forms.ProjectCreateStep2().bindFromRequest().fold(
          hasErrors =>
            Future.successful(FormError(self.showStep2(), hasErrors)),
          formData => {
            pendingProject.settings.save(pendingProject.underlying, formData)

            Future.successful(Redirect(self.showStep3()))
          }
        )
      }
    }
  }


  /**
    *
    * @return
    */
  def showStep3(): Action[AnyContent] = UserLock() async { implicit request =>
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {
        val res = for {
          owner <- OptionT.liftF(pendingProject.underlying.owner.user)
          orgaData <- owner.toMaybeOrganization.semiflatMap(OrganizationData.of)
        } yield {
          Ok(views.creation.step3(pendingProject, owner, pendingProject.file.data.get.authors, orgaData))
        }
        res.getOrElse(NotFound)
      }
    }
  }

  /**
    * Process the post from Step 3
    *
    * @return
    */
  def processStep3(): Action[AnyContent] = UserLock() async { implicit request =>
    // Get the possible pending project
    //TODO: process invitations
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {
        Future.successful(Redirect(self.showStep4()))

      }
    }
  }


  /**
    *
    * @return
    */
  def showStep4(): Action[AnyContent] = UserLock() async { implicit request =>
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {
        val res = for {
          owner <- OptionT.liftF(pendingProject.underlying.owner.user)
          orgaData <- owner.toMaybeOrganization.semiflatMap(OrganizationData.of)
        } yield {
          Ok(views.creation.step4(pendingProject, owner, pendingProject.file.data.get.authors, orgaData))
        }
        res.getOrElse(NotFound)
      }
    }
  }

  /**
    * Process the post from Step 3
    *
    * @return
    */
  def processStep4(): Action[AnyContent] = UserLock() async { implicit request =>
    // Get the possible pending project
    //TODO: process creation
    projectCreationFromUser() match {
      case Left(errorMessage) => {
        Future.successful(Redirect(self.showStep1()).withError(errorMessage))

      }
      case Right(pendingProject) => {
        Future.successful(Redirect(controllers.project.routes.Projects.show(pendingProject.underlying.ownerName, pendingProject.underlying.slug)))

      }
    }
  }


  /**
    * Helper method to get the current PendingProject from the user that is doing the request
    *
    * @param request
    * @return
    */
  def projectCreationFromUser()(implicit request: AuthRequest[_]): Either[String, PendingProjectCreation] = {
    val user = request.user

    // Get key from Cookie
    val key = request.cookies.get("_newproject")
    if (key.isEmpty) {
      Left("error.noFile")

    } else {
      // Get pendingProject
      val pendingProjectOption = this.creationFactory.cacheApi.get[PendingProjectCreation](key.get.value)

      // Check if pending project is from current user
      if (pendingProjectOption.isEmpty || pendingProjectOption.get.file.user.userId != user.userId) {
        Left("error.noFile")

      } else {
        Right(pendingProjectOption.get)
      }
    }
  }

  /*
  def showInvitationForm(author: String, slug: String): Action[AnyContent] = UserLock().async { implicit request =>
    implicit val currentUser: User = request.user

    val authors = pendingProject.file.data.get.authors.toList
    (
      Future.sequence(authors.filterNot(_.equals(currentUser.username)).map(this.users.withName(_).value)),
      this.forums.countUsers(authors),
      pendingProject.underlying.owner.user
    ).parMapN { (users, registered, owner) =>
      Ok(views.invite(owner, pendingProject, users.flatten, registered))
    }
  }

  def showFirstVersionCreator(author: String, slug: String): Action[AnyContent] = UserLock() { implicit request =>
    val res = for {
      pendingProject <- EitherT.fromOption[Id](this.factory.getPendingProject(author, slug), Redirect(self.showCreator()).withError(


        "error.project.timeout"))
      roles <- bindFormEitherT[Id](this.forms.ProjectMemberRoles)(_ => BadRequest: Result)
    } yield {
      pendingProject.roles = roles.build()
      val pendingVersion = pendingProject.pendingVersion
      Redirect(routes.Versions.showCreatorWithMeta(author, slug, pendingVersion.underlying.versionString))
    }

    res.merge
  }
  */
}
