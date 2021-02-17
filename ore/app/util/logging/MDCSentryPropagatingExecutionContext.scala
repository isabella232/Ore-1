package util.logging

import java.util

import scala.concurrent.ExecutionContext

import io.sentry.IHub
import org.slf4j.MDC

//noinspection ScalaDeprecation
trait MDCSentryPropagatingExecutionContext extends ExecutionContext { self =>

  override def prepare(): ExecutionContext = new ExecutionContext {
    private val mdcContext = MDC.getCopyOfContextMap
    private val hub        = FiberSentryImpl._hubs.get()

    override def execute(runnable: Runnable): Unit = self.execute { () =>
      // backup the callee MDC context
      val oldMDCContext = MDC.getCopyOfContextMap
      val oldHub        = FiberSentryImpl._hubs.get()

      // Run the runnable with the captured context
      setMDCSentryData(mdcContext, hub)
      try {
        runnable.run()
      } finally {
        // restore the callee MDC context
        setMDCSentryData(oldMDCContext, oldHub)
      }

    }

    override def reportFailure(cause: Throwable): Unit = self.reportFailure(cause)
  }

  private[this] def setMDCSentryData(mdc: util.Map[String, String], hub: IHub): Unit = {
    if (mdc == null) {
      MDC.clear()
    } else {
      MDC.setContextMap(mdc)
    }

    FiberSentryImpl._hubs.set(FiberSentryImpl.cloneHub(hub))
  }
}
object MDCSentryPropagatingExecutionContext {
  def apply(delegate: ExecutionContext): MDCSentryPropagatingExecutionContext =
    new ExecutionContext with MDCSentryPropagatingExecutionContext {
      override def reportFailure(cause: Throwable): Unit = delegate.reportFailure(cause)
      override def execute(runnable: Runnable): Unit     = delegate.execute(runnable)
    }
}
