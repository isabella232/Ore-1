package ore

import _root_.db.impl.query.SharedQueries
import ore.db.{DbRef, ModelService}
import ore.models.project.{Project, Webhook}

import ackcord.requests.CreateMessageData
import io.circe.Encoder
import io.circe.syntax._
import zio.UIO

object WebhookJobAdder {

  def add[A: Encoder](
      projectId: DbRef[Project],
      projectOwner: String,
      projectSlug: String,
      webhookEvent: Webhook.WebhookEventType,
      data: A,
      discordData: CreateMessageData //TODO: Replace with ExecuteWebhookData
  )(
      implicit service: ModelService[UIO]
  ): UIO[Unit] =
    service
      .runDbCon(
        SharedQueries
          .addWebhookJobs(
            projectId,
            projectOwner,
            projectSlug,
            webhookEvent,
            data.asJson.noSpaces,
            discordData.asJson.noSpaces
          )
          .run
      )
      .unit

}
