package pl.sag.utils

import pl.sag.Logger._

import scala.annotation.tailrec

object XKomParser {

  val productDescriptionStartMark = "Opis produktu"
  val productDescriptionEndMark = "Specyfikacja"
  val productDescriptionEndMark2 = "Opinie"

  val dataCategory = "<li data-category-id"
  val dataCategoryMark = "li"

  val productItem = "<div class=\"product-item"
  val productItemMark = "div"

  val productDetailsTitle = "<div class=\"col-xs-12 product-detail-impression\""
  val productDetailsTitleEndMark = "product-title"
  val productName = "data-product-name=\""
  val productDetailsImg = "<a class=\"prettyphoto-fullscreen-item\""
  val productDetailsImgEndMark = "Zdjęcie 1"
  val productImg = "<img itemprop=\"image\" src=\""

  val cssFilterMark = "main-wrapper"
  val cssFilterMark2 = "<style type=\"text/css\""
  val cssFilterEndMark2 = "</style>"

  val characterMapper = Map(
    "&oacute;"->"ó", "&ndash;" -> "-", "&bdquo;"-> "",
    "&rdquo;" -> "", "&reg;" -> "", "&nbsp;" -> "", "&quot;" -> "\"",
    "&mdash;" -> "-", "&#39;" -> "", "&trade;" -> "", "&rsquo;" -> "'",
    "&#039;" -> "'", "&amp;" -> "&", "&gt;" -> ">")

  def getProductDescription(pageSource: String): Option[String] = {
    pageSource.indexOf(productDescriptionStartMark) match {
      case -1 => {
        log("no product description filter", LogLevel.DEBUG)
        None
      }
      case index => {
        val fullProductDescription = pageSource.substring(
          index + productDescriptionStartMark.length,
          pageSource.indexOf(productDescriptionEndMark) match {
            case -1 => pageSource.indexOf(productDescriptionEndMark2)
            case index => index
          }
        )
        val descriptionWithRemovedMarks = removedMarks(fullProductDescription)
        descriptionWithRemovedMarks.indexOf(cssFilterMark) match {
          case -1 => Some(descriptionWithRemovedMarks)
          case cssIndex => {
            log("wrapper section filter", LogLevel.DEBUG)
            Some(descriptionWithRemovedMarks.substring(0, cssIndex))
          }
        }
      }
    }
  }

  private def getProductInfo(pageSource: String, startMark: String, endMark: String, innerMark: String, logMessage: String) = {
    pageSource.indexOf(startMark) match {
      case -1 => None
      case index => {
        val productDetails = pageSource.substring(
          index,
          pageSource.indexOf(endMark)
        )
        val productInnerIndex = productDetails.indexOf(innerMark)
        log(logMessage, LogLevel.DEBUG)
        val productInnerValue = productDetails.substring(
          productInnerIndex,
          productDetails.substring(productInnerIndex + innerMark.length).indexOf("\"") + productDetails.indexOf(innerMark) + innerMark.length
        ).substring(innerMark.length)
        Some(productInnerValue)
      }
    }
  }

  def getProductTitle(pageSource: String): Option[String] = {
    getProductInfo(pageSource, productDetailsTitle, productDetailsTitleEndMark, productName, "getting title")
  }

  def getProductImgUrl(pageSource: String): Option[String] = {
    getProductInfo(pageSource, productDetailsImg, productDetailsImgEndMark, productImg, "getting imgUrl")
  }

  def getAllInfoInsideMarkWithText(pageSource: String, mark: String, text: String) = {

    def getAllIndexesWithMark(source: String, mark: String, startingIndex: Int): List[Int] = {
      source.indexOf(mark, startingIndex) match {
        case -1 => Nil
        case num => num :: getAllIndexesWithMark(source, mark, num + mark.length)
      }
    }

    def getEndIndexesWithMark(source: String, mark: String, startIndexes: List[Int]) = {
      startIndexes.map(startIndex => source.indexOf(mark, startIndex))
    }

    val startIndexes = getAllIndexesWithMark(pageSource, text, 0)
    val endIndexes = getEndIndexesWithMark(pageSource, "</" + mark + ">", startIndexes)

    startIndexes
      .zip(endIndexes)
      .map { case (startIndex, endIndex) => pageSource.substring(startIndex, endIndex) }
  }

  def cutPageSource(pageSource: String): Option[String] = {
    pageSource.indexOf(productDetailsTitle) match {
      case -1 => None
      case index => {
        Some(
          pageSource.substring(
            index,
            pageSource.indexOf(productDescriptionEndMark) match {
              case -1 => pageSource.indexOf(productDescriptionEndMark2) + productDescriptionEndMark2.length
              case index => index + productDescriptionEndMark.length
            }
          )
        )
      }
    }
  }

  def mapToPolishCharacters(source: Option[String]) = {
    @tailrec
    def map(text: String, mapper: List[(String, String)]): String = {
      mapper match {
        case (from, to) :: rest => map(text.replaceAll(from, to), rest)
        case Nil => text
      }
    }
    source match {
      case None => None
      case Some(text) => Some(map(text, characterMapper.toList))
    }
  }

  def getTextBetween(source: String, mark1: String, mark2: String) = {
    source.substring(
      source.indexOf(mark1),
      source.indexOf(mark2))
  }

  def getLinkInText(source: String) = {
    getTextBetween(source, "a href=\"", ".html").substring(8) + ".html"
  }

  private def removedMarks(fullProductDescription: String) = {
    def removeCssSections(str: String): String = {
      str.indexOf(cssFilterMark2) match {
        case -1 => str
        case index => {
          removeCssSections(str.substring(0, index)+str.substring(str.indexOf(cssFilterEndMark2) + cssFilterEndMark2.length))
        }
      }
    }
    val removedCssProductDescription = removeCssSections(fullProductDescription)

    val sB = StringBuilder.newBuilder
    var isAdding = false
    var isOpen = false
    for (i <- removedCssProductDescription) {
      if (!isOpen) {
        if (i == '>')
          isOpen = true
      }
      else {
        if (i.isLetterOrDigit || i == '.' || i == ',')
          isAdding = true
        else if (i == '<') {
          if (isAdding) {
            sB.append(". ")
          }
          isAdding = false
          isOpen = false
        }
      }
      if (isAdding)
        sB.append(i)
    }
    sB.toString()
  }
}
