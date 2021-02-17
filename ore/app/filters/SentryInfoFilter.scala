package filters

import play.api.http.HeaderNames
import play.api.mvc.{EssentialAction, EssentialFilter, RequestHeader}

import util.logging.FiberSentryImpl

import io.sentry.protocol.{Browser, Device, OperatingSystem}

class SentryInfoFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = (requestHeader: RequestHeader) => {
    FiberSentryImpl.getCurrentHub().configureScope { scope =>
      val transaction = s"${requestHeader.method} ${requestHeader.uri}"
      scope.setTransaction(transaction)

      requestHeader.headers
        .get(HeaderNames.USER_AGENT)
        .map(org.uaparser.scala.Parser.default.parse)
        .foreach { ua =>
          val contexts = scope.getContexts
          def createVersion(pieces: Option[String]*): String = {
            val res = pieces.flatten.mkString(".")
            if (res.isEmpty) null else res
          }

          val device = new Device
          device.setFamily(ua.device.family)
          ua.device.brand.foreach(device.setBrand)
          ua.device.model.foreach(device.setModel)
          contexts.setDevice(device)

          val os = new OperatingSystem
          os.setName(ua.os.family)
          os.setVersion(createVersion(ua.os.major, ua.os.minor, ua.os.patch, ua.os.patchMinor))
          contexts.setOperatingSystem(os)

          val browser = new Browser
          browser.setName(ua.userAgent.family)
          browser.setVersion(createVersion(ua.userAgent.major, ua.userAgent.minor, ua.userAgent.patch))
          contexts.setBrowser(browser)
        }
    }

    next(requestHeader)
  }
}
