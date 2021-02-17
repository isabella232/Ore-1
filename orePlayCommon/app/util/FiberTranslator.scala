package util

import scala.concurrent.Future

import zio.{Has, URIO, ZEnv}

trait FiberTranslator {
  def fiberToFuture[A](fiber: URIO[ZEnv with Has[FiberSentry], A]): Future[A]
}
