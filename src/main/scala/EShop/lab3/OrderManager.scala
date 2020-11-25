package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

object OrderManager {

  sealed trait Command

  case class AddItem(id: String) extends Command

  case class RemoveItem(id: String) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command

  case object Buy extends Command

  case object Pay extends Command

  case class ConfirmCheckoutStarted(checkoutRef: ActorRef) extends Command

  case class ConfirmPaymentStarted(paymentRef: ActorRef) extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Ack

  case object Done extends Ack //trivial ACK
}

class OrderManager extends Actor {

  import OrderManager._

  override def receive: Receive = uninitialized

  implicit val askTimeout: Timeout = Timeout(10 seconds)

  def uninitialized: Receive = {
    case AddItem(item) =>
      val cartActor = context.actorOf(Props[CartActor], "cartActor")
      cartActor ! CartActor.AddItem(item)
      context become open(cartActor)
  }

  def open(cartActor: ActorRef): Receive = {
    case AddItem(item) =>
      cartActor ! CartActor.AddItem(item)

    case RemoveItem(item) =>
      cartActor ! CartActor.RemoveItem(item)

    case Buy =>
      val response = cartActor ? CartActor.StartCheckout
      val result = Await.result(response, askTimeout.duration)
      val checkoutActor = result.asInstanceOf[ConfirmCheckoutStarted].checkoutRef

      checkoutActor ! Checkout.StartCheckout
      context become inCheckout(checkoutActor)
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)

      val response = checkoutActorRef ? Checkout.SelectPayment(payment)
      val result = Await.result(response, askTimeout.duration)
      val paymentActor = result.asInstanceOf[ConfirmPaymentStarted].paymentRef

      context become inPayment(paymentActor)
  }

  def inPayment(paymentActorRef: ActorRef): Receive = {
    case Pay =>
      val response = paymentActorRef ? Payment.DoPayment
      Await.result(response, askTimeout.duration)
      context become finished
  }

  def finished: Receive = {
    case _ =>
      sender ! "order manager finished job"
      context.stop(self)
  }
}
