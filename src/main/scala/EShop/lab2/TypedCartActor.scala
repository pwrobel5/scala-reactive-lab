package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.{actor => classic}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case class StartCheckout(orderManagerRef: classic.ActorRef) extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case class GetItems(sender: ActorRef[Cart]) extends Command

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])
    extends Event

  case object CartTimerKey

  def apply(): Behavior[Command] =
    Behaviors.setup(_ => new TypedCartActor().start)
}

class TypedCartActor() {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 10 seconds

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>
    Behaviors.receiveMessage[TypedCartActor.Command] {
      case AddItem(item) =>
        timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
        nonEmpty(Cart.empty.addItem(item))

      case GetItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
    }
  }

  def nonEmpty(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>
    def stopTimer(): Unit =
      timers.cancel(CartTimerKey)

    def restartTimer(): Unit = {
      stopTimer()
      timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
    }

    Behaviors.setup[TypedCartActor.Command] { context =>
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

        case RemoveItem(item) if !cart.contains(item) =>
          restartTimer()
          Behaviors.same

        case StartCheckout(orderManagerRef) =>
          stopTimer()
          val checkoutActor = context.spawn(TypedCheckout(context.self), "checkout")
          orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
          inCheckout(cart)

        case ExpireCart =>
          println("[TypedCartActor] Cart expired")
          stopTimer()
          empty

        case GetItems(sender) =>
          sender ! cart
          Behaviors.same
      }
    }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.withTimers[TypedCartActor.Command] { timers =>

    Behaviors.receiveMessage[TypedCartActor.Command] {
      case ConfirmCheckoutClosed =>
        empty

      case ConfirmCheckoutCancelled =>
        timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
        nonEmpty(cart)

      case GetItems(sender) =>
        sender ! cart
        Behaviors.same
    }
  }

}
