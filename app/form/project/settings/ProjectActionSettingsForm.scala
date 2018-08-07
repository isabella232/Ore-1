package form.project.settings

case class ProjectActionSettingsForm(
    visibility: Int,
    comment: Option[String]
)
