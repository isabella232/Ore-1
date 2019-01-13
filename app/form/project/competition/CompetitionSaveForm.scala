package form.project.competition

import java.time.LocalDateTime

case class CompetitionSaveForm(
    override val startDate: LocalDateTime,
    override val endDate: LocalDateTime,
    override val timeZoneId: String,
    override val isVotingEnabled: Boolean,
    override val isStaffVotingOnly: Boolean,
    override val shouldShowVoteCount: Boolean,
    override val isSourceRequired: Boolean,
    override val defaultVotes: Int,
    override val staffVotes: Int,
    override val allowedEntries: Int,
    override val maxEntryTotal: Int
) extends CompetitionData
