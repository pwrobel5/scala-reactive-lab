package EShop.lab2

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case object StartCheckout extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])
    extends Event

  case object CartTimerKey

}

class TypedCartActor() {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>
    Behaviors.receiveMessage[TypedCartActor.Command] {
      case AddItem(item) =>
        timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
        nonEmpty(Cart.empty.addItem(item))
    }
  }

  def nonEmpty(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>
    def stopTimer(): Unit =
      timers.cancel(CartTimerKey)

    def restartTimer(): Unit = {
      stopTimer()
      timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
    }

    Behaviors.receiveMessage[TypedCartActor.Command] {
      case AddItem(item) =>
        restartTimer()
        nonEmpty(cart.addItem(item))

      case RemoveItem(item) if cart.size > 1 && cart.contains(item) =>
        restartTimer()
        nonEmpty(cart.removeItem(item))

      case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
        stopTimer()
        empty

      case StartCheckout =>
        stopTimer()
        inCheckout(cart)

      case ExpireCart =>
        stopTimer()
        empty
    }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>

    Behaviors.receiveMessage[TypedCartActor.Command] {
      case ConfirmCheckoutClosed =>
        empty

      case ConfirmCheckoutCancelled =>
        timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
        nonEmpty(cart)
    }
  }

}
