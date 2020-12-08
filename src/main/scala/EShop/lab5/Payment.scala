package EShop.lab5

import java.net.http.HttpTimeoutException
import java.net.{MalformedURLException, SocketException}

import EShop.lab3.Payment.DoPayment
import EShop.lab5.Payment.{PaymentConfirmed, PaymentRejected, PaymentRestarted}
import EShop.lab5.PaymentService.{PaymentClientError, PaymentServerError, PaymentSucceeded}
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props}

import scala.concurrent.duration._

object Payment {

  case object PaymentRejected

  case object PaymentRestarted

  case object PaymentConfirmed

  def props(method: String, orderManager: ActorRef, checkout: ActorRef): Props =
    Props(new Payment(method, orderManager, checkout))

}

class Payment(
               method: String,
               orderManager: ActorRef,
               checkout: ActorRef
             ) extends Actor
  with ActorLogging {

  override def receive: Receive = {
    case DoPayment =>
      context.actorOf(PaymentService.props(method, self))

    case PaymentSucceeded =>
      orderManager ! PaymentConfirmed
      checkout ! PaymentConfirmed
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1.seconds) {
      case _: PaymentClientError =>
        notifyAboutRejection()
        Stop

      case _: PaymentServerError =>
        notifyAboutRestart()
        Restart

      case _: MalformedURLException =>
        notifyAboutRejection()
        Stop

      case _: HttpTimeoutException =>
        notifyAboutRestart()
        Restart

      case _: SocketException =>
        notifyAboutRestart()
        Restart
    }

  //please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(): Unit = {
    orderManager ! PaymentRejected
    checkout ! PaymentRejected
  }

  //please use this one to notify when supervised actor was restarted
  private def notifyAboutRestart(): Unit = {
    orderManager ! PaymentRestarted
    checkout ! PaymentRestarted
  }
}
