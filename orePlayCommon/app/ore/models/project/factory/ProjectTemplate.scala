package ore.models.project.factory

import ore.data.project.Category

case class ProjectTemplate(
    name: String,
    apiV1Identifier: String,
    category: Category,
    description: Option[String]
)
