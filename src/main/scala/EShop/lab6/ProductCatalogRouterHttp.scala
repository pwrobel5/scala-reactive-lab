package EShop.lab6

import EShop.lab5.ProductCatalog.{GetItems, Items}
import EShop.lab5.{ProductCatalog, ProductCatalogHttp, ProductCatalogJsonSupport, SearchService}
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class ProductCatalogRouterHttp extends HttpApp with ProductCatalogJsonSupport {
  implicit val timeout: Timeout = 5 seconds
  private val config = ConfigFactory.load()
  private val productCatalogRouterHttpSystem = ActorSystem(
    "ProductCatalogRouterServer",
    config.getConfig("productcatalog").withFallback(config)
  )
  private val workers = productCatalogRouterHttpSystem.actorOf(
    RoundRobinPool(5).props(ProductCatalog.props(new SearchService())))

  override protected def routes: Route = {
    path("search") {
      post {
        entity(as[ProductCatalogHttp.Query]) { query =>
          val validQuery = GetItems(query.brand, query.productKeyWords)
          val askFuture = workers ? validQuery
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

object ProductCatalogRouterHttpApp extends App {
  new ProductCatalogRouterHttp().startServer("localhost", 9000)
}
