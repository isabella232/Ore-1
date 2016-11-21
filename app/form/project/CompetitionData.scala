package form.project

import java.time.{LocalDateTime, ZoneId}

case class CompetitionData(name: String,
                           description: Option[String],
                           startDate: LocalDateTime,
                           endDate: LocalDateTime,
                           timeZoneId: String,
                           isVotingEnabled: Boolean,
                           isStaffVotingOnly: Boolean,
                           shouldShowVoteCount: Boolean,
                           isSpongeOnly: Boolean,
                           isSourceRequired: Boolean,
                           defaultVotes: Int,
                           staffVotes: Int,
                           allowedEntries: Int,
                           maxEntryTotal: Int) {

  val timeZone: ZoneId = ZoneId.of(this.timeZoneId)

}
