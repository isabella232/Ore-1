package util

import play.api.libs.ws.WSClient

import cats.data.OptionT
import cats.effect.IO

object GitHubUtil {

  private val identifier       = "A-Za-z0-9-_"
  private val gitHubUrlPattern = s"""http(s)?://github.com/[$identifier]+/[$identifier]+(/)?""".r.pattern
  private val readmeApi        = "https://api.github.com/repos/%s/%s/readme"

  def isGitHubUrl(url: String): Boolean = gitHubUrlPattern.matcher(url).matches()

  def getReadme(user: String, project: String)(implicit ws: WSClient): OptionT[IO, String] =
    OptionT(
      IO.fromFuture(IO(ws.url(readmeApi.format(user, project)).get())).map { res =>
        if (res.status == 200) {
          (res.json \ "download_url").validate[String].asOpt
        } else None
      }
    ).semiflatMap(url => IO.fromFuture(IO(ws.url(url).get())))
      .subflatMap(res => if (res.status == 200) res.body else None)
}
