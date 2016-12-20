package db.impl.access

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.{ModelBase, ModelService}
import models.competition.Competition

/**
  * Handles competition based database actions.
  *
  * @param service ModelService instance
  */
class CompetitionBase(implicit val service: ModelService) extends ModelBase[Competition] {

  /**
    * Returns [[ModelAccess]] to all active competitions.
    *
    * @return Access to active competitions
    */
  def active: ModelAccess[Competition] = {
    val now = this.service.theTime
    service.access[Competition](competition => competition.startDate <= now && competition.endDate > now)
  }
}
object CompetitionBase {
  def apply()(implicit organizationBase: CompetitionBase): CompetitionBase = organizationBase

  implicit def fromService(implicit service: ModelService): CompetitionBase = service.competitionBase
}