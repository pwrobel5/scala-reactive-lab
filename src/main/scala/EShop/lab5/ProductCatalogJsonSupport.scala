package EShop.lab5

import java.net.URI

import EShop.lab5.ProductCatalog.Item
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

trait ProductCatalogJsonSupport
  extends SprayJsonSupport
    with DefaultJsonProtocol {
  //custom formatter just for example
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue =
      JsString(obj.toString)

    override def read(json: JsValue): URI = json match {
      case JsString(url) => new URI(url)
      case _ => throw new RuntimeException("Parsing exception")
    }
  }

  implicit val queryFormat: RootJsonFormat[ProductCatalogHttp.Query] =
    jsonFormat2(ProductCatalogHttp.Query)
  implicit val itemFormat: RootJsonFormat[Item] = jsonFormat5(Item)
  implicit val respondFormat: RootJsonFormat[ProductCatalogHttp.Respond] =
    jsonFormat1(ProductCatalogHttp.Respond)
}
