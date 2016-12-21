package db.impl.access

import java.io.File
import java.nio.file.Files._
import java.nio.file.{Path, Paths, StandardCopyOption}

import scala.collection.JavaConverters._

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.{ModelBase, ModelService}
import models.competition.{Competition, CompetitionEntry}
import models.project.{Project, ProjectSettings}
import ore.OreConfig

import cats.data.{EitherT, NonEmptyList}
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Handles competition based database actions.
  *
  * @param service ModelService instance
  */
class CompetitionBase(implicit val service: ModelService, config: OreConfig) extends ModelBase[Competition] {

  private val uploadsDir: Path = Paths.get(this.config.app.uploadsDir)

  /**
    * Returns [[ModelAccess]] to all active competitions.
    *
    * @return Access to active competitions
    */
  def active: ModelAccess[Competition] = {
    val now = this.service.theTime
    service.access[Competition](competition => competition.startDate <= now && competition.endDate > now)
  }

  /**
    * Saves the specified file as the specified competition's banner.
    *
    * @param competition  Competition to set banner for
    * @param file         Banner file
    * @param fileName     Banner file name
    */
  def saveBanner(competition: Competition, file: File, fileName: String): Path = {
    val path = getBannerDir(competition).resolve(fileName)
    if (notExists(path.getParent))
      createDirectories(path.getParent)
    list(path.getParent).iterator().asScala.foreach(delete)
    copy(file.toPath, path, StandardCopyOption.REPLACE_EXISTING)
  }

  /**
    * Returns the directory that contains the specified competition's banner.
    *
    * @param competition  Competition
    * @return             Banner directory
    */
  def getBannerDir(competition: Competition): Path =
    this.uploadsDir.resolve("competitions").resolve(competition.id.value.toString)

  /**
    * Returns the path to the specified competition's banner, if any.
    *
    * @param competition  Competition
    * @return             Banner path, if any, none otherwise
    */
  def getBannerPath(competition: Competition): Option[Path] = {
    val dir = getBannerDir(competition)
    if (exists(dir))
      Option(list(dir).findAny().orElse(null))
    else
      None
  }

  /**
    * Submits the specified Project to the specified Competition.
    *
    * @param project      Project to submit
    * @param comp         Competition to submit project to
    * @return             Error string if any, none otherwise
    */
  def submitProject(project: Project, projectSettings: ProjectSettings, comp: Competition)(
      implicit cs: ContextShift[IO]
  ): EitherT[IO, NonEmptyList[String], CompetitionEntry] = {
    val entries                = comp.entries
    val projectAlreadyEnteredF = entries.exists(_.projectId === project.id.value)
    val projectLimitReachedF   = entries.count(_.userId === project.ownerId).map(_ >= comp.allowedEntries)
    val competitionCapacityReachedF =
      comp.maxEntryTotal.fold(IO.pure(false))(capacity => entries.size.map(_ >= capacity))
    val deadlinePassed = comp.timeRemaining.toSeconds <= 0
    val onlySpongePluginsF =
      if (comp.isSpongeOnly)
        project.recommendedVersion
          .semiflatMap(_.tags)
          .map(_.forall(!_.name.startsWith("Sponge")))
          .getOrElse(true)
      else IO.pure(false)
    val onlyVisibleSource = comp.isSourceRequired && projectSettings.source.isEmpty

    EitherT
      .right[NonEmptyList[String]](
        (
          projectAlreadyEnteredF,
          projectLimitReachedF,
          competitionCapacityReachedF,
          onlySpongePluginsF,
        ).parTupled
      )
      .flatMap {
        case (
            projectAlreadyEntered,
            projectLimitReached,
            competitionCapacityReached,
            onlySpongePlugins,
            ) =>
          val errors = Seq(
            projectAlreadyEntered      -> "error.project.competition.alreadySubmitted",
            projectLimitReached        -> "error.project.competition.entryLimit",
            competitionCapacityReached -> "error.project.competition.capacity",
            deadlinePassed             -> "error.project.competition.over",
            onlySpongePlugins          -> "error.project.competition.spongeOnly",
            onlyVisibleSource          -> "error.project.competition.sourceOnly"
          )

          val applicableErrors = errors.collect {
            case (pred, msg) if pred => msg
          }

          applicableErrors.toList.toNel.fold(
            EitherT.right[NonEmptyList[String]](
              service
                .insert[CompetitionEntry](
                  CompetitionEntry.partial(
                    projectId = project.id.value,
                    userId = project.ownerId,
                    competitionId = comp.id.value
                  )
                )
            )
          )(errs => EitherT.leftT(errs))
      }
  }
}
object CompetitionBase {
  def apply()(implicit organizationBase: CompetitionBase): CompetitionBase = organizationBase

  implicit def fromService(implicit service: ModelService): CompetitionBase = service.competitionBase
}
