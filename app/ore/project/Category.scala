package ore.project

import scala.collection.immutable
import scala.util.Try

import enumeratum.values._

sealed abstract class Category(val value: Int, val title: String, val icon: String, val isVisible: Boolean = true)
    extends IntEnumEntry

/**
  * Enumeration of Categories a Project may possess.
  */
object Category extends IntEnum[Category] {

  val values: immutable.IndexedSeq[Category] = findValues

  case object AdminTools extends Category(0, "Admin Tools", "server")
  case object Chat       extends Category(1, "Chat", "comment")
  case object DevTools   extends Category(2, "Developer Tools", "wrench")
  case object Econ       extends Category(3, "Economy", "money-bill-alt")
  case object Gameplay   extends Category(4, "Gameplay", "puzzle-piece")
  case object Games      extends Category(5, "Games", "gamepad")
  case object Protect    extends Category(6, "Protection", "lock")
  case object Rp         extends Category(7, "Role Playing", "magic")
  case object WorldMgmt  extends Category(8, "World Management", "globe")
  case object Misc       extends Category(9, "Miscellaneous", "asterisk")
  case object Undefined  extends Category(10, "Undefined", null, isVisible = false)

  /**
    * Returns the visible Categories.
    *
    * @return Visible categories
    */
  def visible: Seq[Category] = this.values.filter(_.isVisible).sortBy(_.value)

  /**
    * Returns an Array of categories from a comma separated string of IDs.
    *
    * @param str  Comma separated string of IDs
    * @return     Array of Categories
    */
  def fromString(str: String): Array[Category] =
    str.split(",").flatMap { idStr =>
      val id = Try(idStr.toInt).getOrElse(-1)
      withValueOpt(id)
    }

  def toQueryString(categories: Seq[Category]): String = categories.map(c => c.value).mkString(",")

  def toQueryStringWith(categories: Seq[Category], category: Category): String = {
    var result: Seq[Category] = Seq()
    if (categories.isEmpty || categories.isEmpty) {
      result = Seq(category)
    } else {
      if (categories.contains(category)) {
        result = (categories.toSet - category).toSeq
      } else {
        result = (categories.toSet + category).toSeq
      }
    }
    toQueryString(result)
  }
}
