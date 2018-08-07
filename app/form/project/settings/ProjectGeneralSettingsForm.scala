package form.project.settings

case class ProjectGeneralSettingsForm(
    categoryName: String,
    issues: String,
    source: String,
    licenseName: String,
    licenseUrl: String,
    description: String,
    forumSync: Boolean,
    updateIcon: Boolean
)
