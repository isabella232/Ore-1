import scala.language.higherKinds

import java.sql.Connection
import javax.inject.Provider

import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.cache.{DefaultSyncCacheApi, SyncCacheApi}
import play.api.db.evolutions.EvolutionsComponents
import play.api.db.slick.evolutions.SlickEvolutionsComponents
import play.api.db.slick.{DatabaseConfigProvider, DbName, SlickComponents}
import play.api.http.{HttpErrorHandler, JsonHttpErrorHandler}
import play.api.i18n.MessagesApi
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{
  ApplicationLoader,
  BuiltInComponentsFromContext,
  LoggerConfigurator,
  OptionalSourceMapper,
  Application => PlayApplication
}
import play.filters.HttpFiltersComponents
import play.filters.csp.{CSPConfig, CSPFilter, DefaultCSPProcessor, DefaultCSPResultProcessor}
import play.filters.gzip.{GzipFilter, GzipFilterConfig}

import controllers._
import controllers.apiv2.ApiV2Controller
import controllers.project.{Channels, Pages, Projects, Versions}
import controllers.sugar.Bakery
import db.impl.DbUpdateTask
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.service.OreModelService
import db.impl.service.OreModelService.F
import discourse.{OreDiscourseApi, OreDiscourseApiDisabled, OreDiscourseApiEnabled}
import filters.LoggingFilter
import form.OreForms
import mail.{EmailFactory, Mailer, SpongeMailer}
import ore.auth.{AkkaSSOApi, AkkaSpongeAuthApi, SSOApi, SpongeAuthApi}
import ore.db.ModelService
import ore.discourse.AkkaDiscourseApi
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.markdown.{FlexmarkRenderer, MarkdownRenderer}
import ore.models.project.ProjectTask
import ore.models.project.factory.{OreProjectFactory, ProjectFactory}
import ore.models.project.io.ProjectFiles
import ore.models.user.{FakeUser, UserTask}
import ore.rest.{OreRestfulApiV1, OreRestfulServerV1}
import ore.{OreConfig, OreEnv, StatTracker}
import util.{FileIO, StatusZ, ZIOFileIO}

import ErrorHandler.OreHttpErrorHandler
import akka.actor.ActorSystem
import cats.arrow.FunctionK
import cats.effect.{ContextShift, Resource}
import cats.tagless.syntax.all._
import cats.~>
import com.softwaremill.macwire._
import com.typesafe.scalalogging.Logger
import doobie.{ExecutionContexts, KleisliInterpreter, Transactor}
import doobie.util.transactor.Strategy
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc.{JdbcDataSource, JdbcProfile}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{DefaultRuntime, Task, UIO, ZIO}

class OreApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): PlayApplication = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    new OreComponents(context).application
  }
}

class OreComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with CaffeineCacheComponents
    with SlickComponents
    with SlickEvolutionsComponents
    with EvolutionsComponents {
  val prefix                                = "/"
  override lazy val router: Router          = wire[_root_.router.Routes]
  lazy val apiV2Routes: _root_.apiv2.Routes = wire[_root_.apiv2.Routes]

  use(prefix) //Gets around unused warning
  eager(applicationEvolutions)

  override lazy val httpFilters: Seq[EssentialFilter] = {
    val filters              = super.httpFilters ++ enabledFilters
    val enabledFiltersConfig = configuration.get[Seq[String]]("play.filters.enabled")
    val enabledFiltersCode   = filters.map(_.getClass.getName)

    val notEnabledFilters = enabledFiltersConfig.diff(enabledFiltersCode)

    if (notEnabledFilters.nonEmpty) {
      Logger("Bootstrap").warn(s"Found filters enabled in the config but not in code: $notEnabledFilters")
    }

    filters
  }

  lazy val enabledFilters: Seq[EssentialFilter] = {
    val baseFilters = Seq(
      new CSPFilter(new DefaultCSPResultProcessor(new DefaultCSPProcessor(CSPConfig.fromConfiguration(configuration))))
    )

    val devFilters = Seq(new GzipFilter(GzipFilterConfig.fromConfiguration(configuration)))

    val filterSeq = Seq(
      true                         -> baseFilters,
      context.devContext.isDefined -> devFilters,
      config.ore.logTimings        -> Seq(new LoggingFilter())
    )

    filterSeq.collect {
      case (true, seq) => seq
    }.flatten
  }

  lazy val routerProvider: Provider[Router] = () => router

  lazy val optionalSourceMapper: OptionalSourceMapper = new OptionalSourceMapper(devContext.map(_.sourceMapper))

  override lazy val httpErrorHandler: HttpErrorHandler =
    new ErrorHandler(wire[OreHttpErrorHandler], wire[JsonHttpErrorHandler])

  lazy val syncCacheApi: SyncCacheApi = new DefaultSyncCacheApi(defaultCacheApi)
  lazy val dbConfigProvider: DatabaseConfigProvider = new DatabaseConfigProvider {
    override def get[P <: BasicProfile]: DatabaseConfig[P] = slickApi.dbConfig(DbName("default"))
  }
  implicit lazy val impMessagesApi: MessagesApi = messagesApi
  implicit lazy val impActorSystem: ActorSystem = actorSystem

  implicit lazy val runtime: DefaultRuntime = new DefaultRuntime {}

  type ParUIO[A]  = zio.interop.ParIO[Any, Nothing, A]
  type ParTask[A] = zio.interop.ParIO[Any, Throwable, A]

  val taskToUIO: Task ~> UIO = OreComponents.orDieFnK[Any]
  val uioToTask: UIO ~> Task = OreComponents.upcastFnK[UIO, Task]

  implicit lazy val config: OreConfig                              = wire[OreConfig]
  implicit lazy val env: OreEnv                                    = wire[OreEnv]
  implicit lazy val markdownRenderer: MarkdownRenderer             = wire[FlexmarkRenderer]
  implicit lazy val fileIORaw: FileIO[ZIO[Blocking, Throwable, ?]] = ZIOFileIO(config)

  implicit lazy val fileIO: FileIO[ZIO[Blocking, Nothing, ?]] = fileIORaw.imapK(OreComponents.orDieFnK[Blocking])(
    OreComponents.upcastFnK[ZIO[Blocking, Nothing, ?], ZIO[Blocking, Throwable, ?]]
  )

  implicit lazy val projectFiles: ProjectFiles[ZIO[Blocking, Nothing, ?]] =
    wire[ProjectFiles.LocalProjectFiles[ZIO[Blocking, Nothing, ?]]]

  implicit val transactor: Transactor[Task] = {
    val cs = ContextShift[Task]

    applicationResource {
      for {
        connectEC  <- ExecutionContexts.fixedThreadPool[F](32)
        transactEC <- ExecutionContexts.cachedThreadPool[F]
      } yield Transactor[F, JdbcDataSource](
        dbConfigProvider.get[JdbcProfile].db.source,
        source => {
          val acquire                = cs.evalOn(connectEC)(F.delay(source.createConnection()))
          def release(c: Connection) = cs.evalOn(transactEC)(F.delay(c.close()))
          Resource.make(acquire)(release)
        },
        KleisliInterpreter[F](transactEC).ConnectionInterpreter,
        Strategy.default
      )
    }
  }

  lazy val oreRestfulAPIV1: OreRestfulApiV1                          = wire[OreRestfulServerV1]
  implicit lazy val projectFactory: ProjectFactory                   = wire[OreProjectFactory]
  implicit lazy val modelService: ModelService[UIO]                  = wire[OreModelService].mapK(taskToUIO)
  lazy val emailFactory: EmailFactory                                = wire[EmailFactory]
  lazy val mailer: Mailer                                            = wire[SpongeMailer]
  lazy val projectTask: ProjectTask                                  = wire[ProjectTask]
  lazy val userTask: UserTask                                        = wire[UserTask]
  lazy val dbUpdateTask: DbUpdateTask                                = wire[DbUpdateTask]
  implicit lazy val oreControllerComponents: OreControllerComponents = wire[DefaultOreControllerComponents]
  lazy val uioOreControllerEffects: OreControllerEffects[UIO]        = wire[DefaultOreControllerEffects[UIO]]

  lazy val statTracker: StatTracker[UIO] = (wire[StatTracker.StatTrackerInstant[Task, ParTask]]: StatTracker[Task])
    .imapK(taskToUIO)(uioToTask)
  lazy val spongeAuthApiTask: SpongeAuthApi[Task] = {
    val api = config.security.api
    runtime.unsafeRun(
      AkkaSpongeAuthApi[Task](
        AkkaSpongeAuthApi.AkkaSpongeAuthSettings(
          api.key,
          api.url,
          api.breaker.maxFailures,
          api.breaker.reset,
          api.breaker.timeout
        )
      )
    )
  }
  implicit lazy val spongeAuthApi: SpongeAuthApi[UIO] = spongeAuthApiTask.mapK(taskToUIO)
  lazy val ssoApiTask: SSOApi[Task] = {
    val sso = config.security.sso
    runtime.unsafeRun(AkkaSSOApi[Task](sso.loginUrl, sso.signupUrl, sso.verifyUrl, sso.secret, sso.timeout, sso.reset))
  }
  lazy val ssoApi: SSOApi[UIO] = ssoApiTask.imapK(taskToUIO)(uioToTask)
  lazy val oreDiscourseApiTask: OreDiscourseApi[Task] = {
    val forums = config.forums
    if (forums.api.enabled) {
      val api = forums.api

      val discourseApi = runtime.unsafeRun(
        AkkaDiscourseApi[Task](
          AkkaDiscourseSettings(
            api.key,
            api.admin,
            forums.baseUrl,
            api.breaker.maxFailures,
            api.breaker.reset,
            api.breaker.timeout
          )
        )
      )

      val forumsApi = new OreDiscourseApiEnabled(
        discourseApi,
        forums.categoryDefault,
        forums.categoryDeleted,
        env.conf.resolve("discourse/project_topic.md"),
        env.conf.resolve("discourse/version_post.md"),
        forums.retryRate,
        actorSystem.scheduler,
        forums.baseUrl,
        api.admin
      )

      forumsApi.start()

      forumsApi
    } else {
      new OreDiscourseApiDisabled[Task]
    }
  }
  implicit lazy val oreDiscourseApi: OreDiscourseApi[UIO] = oreDiscourseApiTask.mapK(taskToUIO)
  implicit lazy val userBaseTask: UserBase[Task]          = wire[UserBase.UserBaseF[Task]]
  implicit lazy val userBaseUIO: UserBase[UIO]            = wire[UserBase.UserBaseF[UIO]]
  implicit lazy val projectBase: ProjectBase[UIO] = {
    implicit val providedProjectFiles: ProjectFiles[Task] =
      projectFiles.mapK(OreComponents.provideFnK[Blocking, Nothing](runtime.Environment))

    implicit val throwableFileIO: FileIO[Task] = fileIO.imapK(new FunctionK[ZIO[Blocking, Nothing, ?], Task] {
      override def apply[A](fa: ZIO[Blocking, Nothing, A]): Task[A] = fa.provide(runtime.Environment)
    })(new FunctionK[Task, ZIO[Blocking, Nothing, ?]] {
      override def apply[A](fa: Task[A]): ZIO[Blocking, Nothing, A] = fa.orDie
    })

    // Schrodinger's values, are both used and not used at the same time.
    // Trying to observe if they are will collapse the compile state into an error.
    use(providedProjectFiles)
    use(throwableFileIO)

    (wire[ProjectBase.ProjectBaseF[Task, ParTask]]: ProjectBase[Task]).mapK(taskToUIO)
  }
  implicit lazy val orgBase: OrganizationBase[UIO] =
    (wire[OrganizationBase.OrganizationBaseF[Task, ParTask]]: OrganizationBase[Task]).mapK(taskToUIO)

  lazy val bakery: Bakery     = wire[Bakery]
  lazy val forms: OreForms    = wire[OreForms]
  lazy val statusZ: StatusZ   = wire[StatusZ]
  lazy val fakeUser: FakeUser = wire[FakeUser]

  lazy val applicationController: Application                   = wire[Application]
  lazy val apiV1Controller: ApiV1Controller                     = wire[ApiV1Controller]
  lazy val apiV2Controller: ApiV2Controller                     = wire[ApiV2Controller]
  lazy val versions: Versions                                   = wire[Versions]
  lazy val users: Users                                         = wire[Users]
  lazy val projects: Projects                                   = wire[Projects]
  lazy val pages: Pages                                         = wire[Pages]
  lazy val organizations: Organizations                         = wire[Organizations]
  lazy val channels: Channels                                   = wire[Channels]
  lazy val reviews: Reviews                                     = wire[Reviews]
  lazy val applicationControllerProvider: Provider[Application] = () => applicationController
  lazy val apiV1ControllerProvider: Provider[ApiV1Controller]   = () => apiV1Controller
  lazy val apiV2ControllerProvider: Provider[ApiV2Controller]   = () => apiV2Controller
  lazy val versionsProvider: Provider[Versions]                 = () => versions
  lazy val usersProvider: Provider[Users]                       = () => users
  lazy val projectsProvider: Provider[Projects]                 = () => projects
  lazy val pagesProvider: Provider[Pages]                       = () => pages
  lazy val organizationsProvider: Provider[Organizations]       = () => organizations
  lazy val channelsProvider: Provider[Channels]                 = () => channels
  lazy val reviewsProvider: Provider[Reviews]                   = () => reviews

  eager(projectTask)
  eager(userTask)
  eager(dbUpdateTask)

  def eager[A](module: A): Unit = use(module)

  def use[A](value: A): Unit = {
    identity(value)
    ()
  }

  def applicationResource[A](resource: Resource[Task, A]): A = {
    val (a, finalize) = runtime.unsafeRunSync(resource.allocated).toEither.toTry.get

    applicationLifecycle.addStopHook { () =>
      runtime.unsafeRunToFuture(finalize)
    }

    a
  }
}
object OreComponents {
  //macwire doesn't seem to like this function
  def upcastFnK[From[_], To[A] >: From[A]]: From ~> To = new FunctionK[From, To] {
    override def apply[A](fa: From[A]): To[A] = fa
  }
  def provideFnK[R, E](environment: R): ZIO[R, E, ?] ~> ZIO[Any, E, ?] = new FunctionK[ZIO[R, E, ?], ZIO[Any, E, ?]] {
    override def apply[A](fa: ZIO[R, E, A]): ZIO[Any, E, A] = fa.provide(environment)
  }
  def orDieFnK[R]: ZIO[R, Throwable, ?] ~> ZIO[R, Nothing, ?] =
    new FunctionK[ZIO[R, Throwable, ?], ZIO[R, Nothing, ?]] {
      override def apply[A](fa: ZIO[R, Throwable, A]): ZIO[R, Nothing, A] = fa.orDie
    }
}
