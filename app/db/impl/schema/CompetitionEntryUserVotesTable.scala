package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import models.competition.CompetitionEntry
import models.user.User

class CompetitionEntryUserVotesTable(tag: Tag)
    extends AssociativeTable[CompetitionEntry, User](tag, "project_competition_entry_votes") {

  def userId  = column[DbRef[User]]("user_id")
  def entryId = column[DbRef[CompetitionEntry]]("entry_id")

  override def * = (entryId, userId)

}
