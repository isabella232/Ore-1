package util.logging

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{Collections, Date}

import scala.jdk.CollectionConverters._

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, ThrowableProxy}
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.sentry._
import io.sentry.logback.BuildConfig
import io.sentry.protocol.{Message, SdkVersion}
import zio.FiberFailure

// Copy of SentryAppender, but sends events to the fiber local ZIO hubs instead of Sentry's thread local hubs
// Also handles Fiber failures specially, and makes the options available outside the appender
class ZIOSentryAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {
  private var options: SentryOptions        = new SentryOptions
  private var minimumBreadcrumbLevel: Level = Level.INFO
  private var minimumEventLevel: Level      = Level.ERROR

  val IsoFormatWithMillis = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

  override def start(): Unit = {
    options.setEnableExternalConfiguration(true)
    options.setSentryClientName(BuildConfig.SENTRY_LOGBACK_SDK_NAME)
    options.setSdkVersion(createSdkVersion(options))
    try {
      Sentry.init(options)
      FiberSentryImpl.start(new Hub(options))
    } catch {
      case e: IllegalArgumentException =>
        addWarn("Failed to init Sentry during appender initialization: " + e.getMessage)
    }

    super.start()
  }

  override protected def append(eventObject: ILoggingEvent): Unit = {
    if (eventObject.getLevel.isGreaterOrEqual(minimumEventLevel)) {
      FiberSentryImpl.getCurrentHub().captureEvent(createEvent(eventObject))
    }
    if (eventObject.getLevel.isGreaterOrEqual(minimumBreadcrumbLevel)) {
      FiberSentryImpl.getCurrentHub().addBreadcrumb(createBreadcrumb(eventObject))
    }
  }

  /**
    * Creates [[SentryEvent]] from Logback's [[ILoggingEvent]].
    *
    * @param loggingEvent the logback event
    * @return the sentry event
    */
  private def createEvent(loggingEvent: ILoggingEvent): SentryEvent = {
    val event: SentryEvent = new SentryEvent(new Date(loggingEvent.getTimeStamp))
    val message: Message   = new Message
    message.setMessage(loggingEvent.getMessage)
    message.setFormatted(loggingEvent.getFormattedMessage)
    message.setParams(toParams(loggingEvent.getArgumentArray))
    event.setMessage(message)
    event.setLogger(loggingEvent.getLoggerName)
    event.setLevel(formatLevel(loggingEvent.getLevel))

    val throwableInformation: ThrowableProxy = loggingEvent.getThrowableProxy.asInstanceOf[ThrowableProxy]
    if (throwableInformation != null) {
      throwableInformation.getThrowable match {
        case e: FiberFailure if e.cause.died =>
          event.setThrowable(e.cause.dieOption.get)
        case e =>
          event.setThrowable(e)
      }
    }

    if (loggingEvent.getThreadName != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName)
    }

    val mdcProperties: util.Map[String, String] = new ConcurrentHashMap(loggingEvent.getMDCPropertyMap)
    if (!mdcProperties.isEmpty) {
      event.getContexts.put("MDC", mdcProperties)
    }

    event
  }

  private def toParams(arguments: Array[AnyRef]): util.List[String] = {
    if (arguments != null) {
      arguments
        .collect { case arg if arg != null => arg.toString }
        .toSeq
        .asJava
    } else {
      Collections.emptyList
    }
  }

  /**
    * Creates [[Breadcrumb]] from Logback's [[ILoggingEvent]].
    *
    * @param loggingEvent the logback event
    * @return the sentry breadcrumb
    */
  private def createBreadcrumb(loggingEvent: ILoggingEvent): Breadcrumb = {
    val breadcrumb: Breadcrumb = new Breadcrumb
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel))
    breadcrumb.setCategory(loggingEvent.getLoggerName)
    breadcrumb.setMessage(loggingEvent.getFormattedMessage)
    breadcrumb
  }

  /**
    * Transforms a [[Level]] into an [[SentryLevel]].
    *
    * @param level original level as defined in log4j.
    * @return log level used within sentry.
    */
  private def formatLevel(level: Level): SentryLevel =
    if (level.isGreaterOrEqual(Level.ERROR)) SentryLevel.ERROR
    else if (level.isGreaterOrEqual(Level.WARN)) SentryLevel.WARNING
    else if (level.isGreaterOrEqual(Level.INFO)) SentryLevel.INFO
    else SentryLevel.DEBUG

  private def createSdkVersion(sentryOptions: SentryOptions): SdkVersion = {
    var sdkVersion: SdkVersion = sentryOptions.getSdkVersion

    if (sdkVersion == null) {
      sdkVersion = new SdkVersion
    }

    sdkVersion.setName(BuildConfig.SENTRY_LOGBACK_SDK_NAME)
    val version: String = BuildConfig.VERSION_NAME
    sdkVersion.setVersion(version)
    sdkVersion.addPackage("maven:sentry-logback", version)

    sdkVersion
  }

  def setOptions(options: SentryOptions): Unit =
    this.options = options

  def setMinimumBreadcrumbLevel(minimumBreadcrumbLevel: Level): Unit =
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel
    }

  def getMinimumBreadcrumbLevel: Level =
    minimumBreadcrumbLevel

  def setMinimumEventLevel(minimumEventLevel: Level): Unit =
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel
    }

  def getMinimumEventLevel: Level =
    minimumEventLevel
}
