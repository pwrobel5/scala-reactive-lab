package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.ask
import akka.util.Timeout
import akka.{actor => classic}

import scala.concurrent.Await
import scala.concurrent.duration._

object OrderManager {

  sealed trait Command

  case class AddItem(id: String) extends Command

  case class RemoveItem(id: String) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command

  case object Buy extends Command

  case object Pay extends Command

  case class ConfirmCheckoutStarted(checkoutRef: typed.ActorRef[TypedCheckout.Command]) extends Command

  case class ConfirmPaymentStarted(paymentRef: classic.ActorRef) extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Ack

  case object Done extends Ack //trivial ACK
}

class OrderManager extends classic.Actor {

  import OrderManager._

  override def receive: Receive = uninitialized

  implicit val askTimeout: Timeout = Timeout(10 seconds)

  def uninitialized: Receive = {
    case AddItem(item) =>
      val cartActor = context.spawn(TypedCartActor(), "typedCartActor")
      cartActor ! TypedCartActor.AddItem(item)
      sender ! Done
      context become open(cartActor)
  }

  def open(cartActor: typed.ActorRef[TypedCartActor.Command]): Receive = {
    case AddItem(item) =>
      cartActor ! TypedCartActor.AddItem(item)
      sender ! Done

    case RemoveItem(item) =>
      cartActor ! TypedCartActor.RemoveItem(item)
      sender ! Done

    case Buy =>
      cartActor ! TypedCartActor.StartCheckout(context.self)
      context become inCheckout(cartActor, sender)
  }

  def inCheckout(cartActorRef: typed.ActorRef[TypedCartActor.Command], senderRef: classic.ActorRef): Receive = {
    case ConfirmCheckoutStarted(checkoutRef) =>
      checkoutRef ! TypedCheckout.StartCheckout
      senderRef ! Done
      context become inCheckout(checkoutRef)
  }

  def inCheckout(checkoutActorRef: typed.ActorRef[TypedCheckout.Command]): Receive = {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
      context become inPayment(sender)
  }

  def inPayment(senderRef: classic.ActorRef): Receive = {
    case ConfirmPaymentStarted(paymentActorRef) =>
      senderRef ! Done
      context become inPayment(paymentActorRef, senderRef)
  }

  def inPayment(paymentActorRef: classic.ActorRef, senderRef: classic.ActorRef): Receive = {
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
