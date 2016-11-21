package form.project

import java.util.Date

case class CompetitionData(name: String,
                           description: Option[String],
                           startDate: Date,
                           endDate: Date,
                           isVotingEnabled: Boolean,
                           isStaffVotingOnly: Boolean,
                           shouldShowVoteCount: Boolean,
                           isSpongeOnly: Boolean,
                           isSourceRequired: Boolean,
                           defaultVotes: Int,
                           staffVotes: Int,
                           allowedEntries: Int,
                           maxEntryTotal: Int)
