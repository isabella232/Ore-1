package util

import play.api.libs.ws.WSClient

import cats.data.OptionT
import cats.effect.IO

object GitHubUtil {

  private val identifier       = "A-Za-z0-9-_"
  private val gitHubUrlPattern = s"""http(s)?://github.com/[$identifier]+/[$identifier]+(/)?""".r.pattern
  private val readmeUrl        = "https://raw.githubusercontent.com/%s/%s/master/README.md"

  def isGitHubUrl(url: String): Boolean = gitHubUrlPattern.matcher(url).matches()

  def getReadme(user: String, project: String)(implicit ws: WSClient): OptionT[IO, String] =
    OptionT(
      IO.fromFuture(IO(ws.url(readmeUrl.format(user, project)).get())).map { res =>
        if (res.status == 200) Some(res.body) else None
      }
    )

}
