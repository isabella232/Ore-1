package util.logging

import scala.concurrent.{ExecutionContext, Future}

import util.{FiberSentry, FiberTranslator}

import org.slf4j.MDC
import zio.{Has, URIO, ZEnv, ZIO}

class FiberTranslatorImpl(runtime: zio.Runtime[ZEnv with Has[FiberSentry]])(implicit executionContext: ExecutionContext)
    extends FiberTranslator {
  override def fiberToFuture[A](fiber: URIO[ZEnv with Has[FiberSentry], A]): Future[A] = {
    val context = MDC.getCopyOfContextMap
    val sentry  = FiberSentryImpl._hubs.get()

    val program = ZIO.effectTotal {
      MDC.setContextMap(context)
      FiberSentryImpl._hubs.set(FiberSentryImpl.cloneHub(sentry))
    } *> (fiber <*> ZIO.effectTotal((MDC.getCopyOfContextMap, FiberSentryImpl._hubs.get())))

    runtime.unsafeRunToFuture(program).map {
      case (a, (mdc, hub)) =>
        MDC.setContextMap(mdc)
        FiberSentryImpl._hubs.set(FiberSentryImpl.cloneHub(hub))
        a
    }
  }
}
