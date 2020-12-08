package EShop.lab5

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef): Props = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging {

  import PaymentService._
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  private val URI = getURI

  override def preStart(): Unit =
    http.singleRequest(HttpRequest(uri = URI)).pipeTo(self)

  override def receive: Receive = {
    case HttpResponse(StatusCodes.OK, _, _, _) =>
      payment ! PaymentSucceeded
      context.stop(self)

    case HttpResponse(StatusCodes.BAD_REQUEST, _, _, _) =>
      throw new PaymentClientError()

    case HttpResponse(StatusCodes.NOT_FOUND, _, _, _) =>
      throw new PaymentClientError()

    case HttpResponse(StatusCodes.REQUEST_TIMEOUT, _, _, _) =>
      throw new PaymentServerError()

    case HttpResponse(StatusCodes.INTERNAL_SERVER_ERROR, _, _, _) =>
      throw new PaymentServerError()

    case HttpResponse(StatusCodes.IM_A_TEAPOT, _, _, _) =>
      throw new PaymentServerError()
  }

  private def getURI: String = method match {
    case "payu" => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa" => s"http://httpbin.org/status/200"
    case _ => s"http://httpbin.org/status/404"
  }

}
