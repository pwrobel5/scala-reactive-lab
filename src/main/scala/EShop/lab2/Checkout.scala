package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{ActorRef, Cancellable, Props, Timers}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command

  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef): Props = Props(new Checkout())

  case object CheckoutTimerKey

  case object PaymentTimerKey

}

class Checkout extends Timers {

  private val scheduler = context.system.scheduler
  private val log = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 10 seconds
  val paymentTimerDuration: FiniteDuration = 10 seconds

  def startCheckoutTimer(): Unit =
    timers.startSingleTimer(CheckoutTimerKey, ExpireCheckout, checkoutTimerDuration)

  def stopCheckoutTimer(): Unit =
    timers.cancel(CheckoutTimerKey)

  def restartCheckoutTimer(): Unit = {
    stopCheckoutTimer()
    startCheckoutTimer()
  }

  def startPaymentTimer(): Unit =
    timers.startSingleTimer(PaymentTimerKey, ExpirePayment, paymentTimerDuration)

  def stopPaymentTimer(): Unit =
    timers.cancel(PaymentTimerKey)

  def restartPaymentTimer(): Unit = {
    stopPaymentTimer()
    startPaymentTimer()
  }

  def receive: Receive = {
    case StartCheckout =>
      startCheckoutTimer()
      context become selectingDelivery
  }

  def selectingDelivery: Receive = {
    case SelectDeliveryMethod(_) =>
      restartCheckoutTimer()
      context become selectingPaymentMethod

    case CancelCheckout =>
      stopCheckoutTimer()
      context become cancelled

    case ExpireCheckout =>
      context become cancelled
  }

  def selectingPaymentMethod: Receive = {
    case SelectPayment(_) =>
      stopCheckoutTimer()
      startPaymentTimer()
      context become processingPayment

    case CancelCheckout =>
      stopCheckoutTimer()
      context become cancelled

    case ExpireCheckout =>
      context become cancelled
  }

  def processingPayment: Receive = {
    case ConfirmPaymentReceived =>
      stopPaymentTimer()
      context become closed

    case CancelCheckout =>
      stopPaymentTimer()
      context become cancelled

    case ExpirePayment =>
      context become cancelled
  }

  def cancelled: Receive = {
    case _ =>
      context.stop(self)
  }

  def closed: Receive = {
    case _ =>
      context.stop(self)
  }

}
