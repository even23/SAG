package pl.sag.mainActor

import java.io.{File, PrintWriter}

import akka.actor.{Actor, ActorRef, Props}
import javax.inject.Inject
import pl.sag.Logger._
import pl.sag._
import pl.sag.product.{ProductInfo, ProductsInfo}
import pl.sag.subActor.SubActor
import pl.sag.utils.{FileManager, XKomClient}

import scala.collection.mutable


class MainActor (val numberOfSubActors: Int) extends Actor {

  private var subActors = mutable.Buffer[ActorRef]()
  private val actorsToProducts = mutable.HashMap[ActorRef, Option[ProductsInfo]]()

  override def receive: Receive = {
    case StartCollectingData => startCollectingData()
    case SendCollectedProductsInfoToMainActor(productsInfo) => saveProductsInfo(productsInfo)
    case SendBestMatchesToMainActor(topMatches) => displayBestMatches(topMatches)
    case GetBestMatches(productUrl) => getBestMatches(productUrl)
    case ShowProductsInfo => showProductsInfo()
    case TerminateChildren => subActors.foreach(context.stop)
    case CheckIfGotAllMessages => isAllDataDownloaded()
    case ShowCurrentLinksAndImgsOfProducts => showCurrentProducts()
    case UpdateLocalBaseCategoriesAndProductsLinks => updateLocalBase()
  }

  def createSubActor() = {
    log("Creating subActor" + "subActor" + subActors.length)
    subActors += context.actorOf(Props[SubActor], "subActor" + subActors.length)
  }

  def startCollectingData() = {
    for (_ <- 0 until numberOfSubActors)
      createSubActor()
    log("Starting collecting data for " + subActors.length + " subActors.")
    subActors.foreach(_ ! CollectData)
  }

  def getBestMatches(productUrl: String) = {
    subActors.foreach(_ ! GetBestMatches(productUrl))
  }

  def saveProductsInfo(productsInfo: ProductsInfo) = {
    actorsToProducts += (sender -> Some(productsInfo))
  }

  def displayBestMatches(topMatches: Seq[(ProductInfo, Double)]) = {
    if (topMatches.isEmpty)
      println("Couldn't find any relevant documents")
    else {
      println(s"Top results:")
      topMatches.foreach(println)
    }
  }

  def isAllDataDownloaded() = {
    if (actorsToProducts.size != subActors.size)
      log("MainActor is waiting for data")
    else
    log("MainActor got all data")
  }

  def showProductsInfo() = {
    log("Printing products")
    actorsToProducts.foreach {
      case (actorRef, productsInfo) => log(actorRef.path.name, productsInfo.map(_.productsInfo).toString)
    }

    log("Sorted Data: ", actorsToProducts.flatMap(_._2).flatMap(_.productsInfo).toList.sortBy(_.linkPage).toString)
  }

  def showCurrentProducts() = {
    actorsToProducts.flatMap(_._2).flatMap(_.productsInfo).foreach(println)
  }

  def updateLocalBase() = {
    val xKomClient = new XKomClient(false)
    createDirectories()
    val writer = new PrintWriter(FileManager.linksFile)
    val categoryToProducts = xKomClient.categoriesLinks.par
      .map(cat => {
        val products = xKomClient.getProductLinks(cat)
        writer.write(cat)
        products.foreach(p => writer.write(" " + p))
        writer.println()
        cat -> products
      })
      .toList.toMap
    writer.close()
    println(s"Saved ${categoryToProducts.size} links to categories and ${categoryToProducts.values.flatten.size} links to products")
  }

  private def createDirectories() = {
    new File(FileManager.mainFolder).mkdir()
    new File(FileManager.productsFolder).mkdir()
    new File(FileManager.linksFile).createNewFile()
  }
}
