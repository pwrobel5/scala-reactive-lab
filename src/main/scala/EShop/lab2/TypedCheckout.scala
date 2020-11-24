package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {

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

  case class PaymentStarted(payment: ActorRef[Any]) extends Event

  case object CheckoutTimerKey

  case object PaymentTimerKey

  def apply(): Behavior[Command] =
    Behaviors.setup(_ => new TypedCheckout().start)

}

class TypedCheckout {

  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 10 seconds
  val paymentTimerDuration: FiniteDuration = 10 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.withTimers[TypedCheckout.Command] { timers =>
    Behaviors.receiveMessage[TypedCheckout.Command] {
      case StartCheckout =>
        timers.startSingleTimer(CheckoutTimerKey, ExpireCheckout, checkoutTimerDuration)
        selectingDelivery
    }
  }

  def selectingDelivery: Behavior[TypedCheckout.Command] = Behaviors.withTimers[TypedCheckout.Command] { timers =>
    Behaviors.receiveMessage[TypedCheckout.Command] {
      case SelectDeliveryMethod(_) =>
        timers.cancel(CheckoutTimerKey)
        timers.startSingleTimer(CheckoutTimerKey, ExpireCheckout, checkoutTimerDuration)
        selectingPaymentMethod

      case CancelCheckout =>
        timers.cancel(CheckoutTimerKey)
        cancelled

      case ExpireCheckout =>
        cancelled
    }
  }

  def selectingPaymentMethod: Behavior[TypedCheckout.Command] = Behaviors.withTimers[TypedCheckout.Command] { timers =>
    Behaviors.receiveMessage[TypedCheckout.Command] {
      case SelectPayment(_) =>
        timers.cancel(CheckoutTimerKey)
        timers.startSingleTimer(PaymentTimerKey, ExpirePayment, paymentTimerDuration)
        processingPayment

      case CancelCheckout =>
        timers.cancel(CheckoutTimerKey)
        cancelled

      case ExpireCheckout =>
        cancelled
    }
  }

  def processingPayment: Behavior[TypedCheckout.Command] = Behaviors.withTimers[TypedCheckout.Command] { timers =>
    Behaviors.receiveMessage[TypedCheckout.Command] {
      case ConfirmPaymentReceived =>
        timers.cancel(PaymentTimerKey)
        closed

      case CancelCheckout =>
        timers.cancel(PaymentTimerKey)
        cancelled

      case ExpirePayment =>
        cancelled
    }
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage[TypedCheckout.Command](_ => {
    println("[TypedCheckoutActor] Checkout cancelled")
    Behaviors.stopped
  })

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage[TypedCheckout.Command](_ => {
    println("[TypedCheckoutActor] Checkout closed")
    Behaviors.stopped
  })

}
