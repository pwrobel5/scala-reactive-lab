package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

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
      sender ! Done
      context become open(cartActor)
  }

  def open(cartActor: ActorRef): Receive = {
    case AddItem(item) =>
      cartActor ! CartActor.AddItem(item)
      sender ! Done

    case RemoveItem(item) =>
      cartActor ! CartActor.RemoveItem(item)
      sender ! Done

    case Buy =>
      cartActor ! CartActor.StartCheckout
      context become inCheckout(cartActor, sender)
  }

  def inCheckout(cartActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case ConfirmCheckoutStarted(checkoutActor) =>
      senderRef ! Done
      checkoutActor ! Checkout.StartCheckout
      context become inCheckout(checkoutActor)
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)

      context become inPayment(sender)
  }

  def inPayment(senderRef: ActorRef): Receive = {
    case ConfirmPaymentStarted(paymentActor) =>
      senderRef ! Done
      context become inPayment(paymentActor, senderRef)
  }

  def inPayment(paymentActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case Pay =>
      val response = paymentActorRef ? Payment.DoPayment
      Await.result(response, askTimeout.duration)
      sender ! Done
      context become finished
  }

  def finished: Receive = {
    case _ =>
      sender ! "order manager finished job"
      context.stop(self)
  }
}
