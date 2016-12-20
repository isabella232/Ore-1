package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.competition.{Competition, CompetitionEntry}
import models.project.Project
import models.user.User

class CompetitionEntryTable(tag: Tag) extends ModelTable[CompetitionEntry](tag, "project_competition_entries") {

  def projectId     = column[DbRef[Project]]("project_id")
  def userId        = column[DbRef[User]]("user_id")
  def competitionId = column[DbRef[Competition]]("competition_id")

  override def * = mkProj((id.?, createdAt.?, projectId, userId, competitionId))(
    mkTuple[CompetitionEntry]()
  )

}
