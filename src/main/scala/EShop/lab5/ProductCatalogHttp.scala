package EShop.lab5

import EShop.lab5.ProductCatalog.{GetItems, Item, Items}
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object ProductCatalogHttp {

  case class Query(brand: String, productKeyWords: List[String])

  case class Respond(items: List[Item])

}

class ProductCatalogHttp extends HttpApp with ProductCatalogJsonSupport {
  implicit val timeout: Timeout = 5 seconds
  private val config = ConfigFactory.load()
  private val productCatalogHttpSystem = ActorSystem(
    "ProductCatalogServer",
    config.getConfig("productcatalogserver").withFallback(config)
  )
  private val productCatalog = productCatalogHttpSystem.actorSelection(
    "akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")

  override protected def routes: Route = {
    path("search") {
      post {
        entity(as[ProductCatalogHttp.Query]) { query =>
          val validQuery = GetItems(query.brand, query.productKeyWords)
          val askFuture = productCatalog ? validQuery
          val queryResult =
            Await.result(askFuture, timeout.duration).asInstanceOf[Items]

          complete {
            Future.successful(ProductCatalogHttp.Respond(queryResult.items))
          }
        }
      }
    }
  }
}

object ProductCatalogHttpApp extends App {
  new ProductCatalogHttp().startServer("localhost", 9000)
}
