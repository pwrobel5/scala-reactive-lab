package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.{ActorRef, Props, Timers}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case object StartCheckout extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case object GetItems extends Command

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event

  case class ItemAdded(itemId: Any, cart: Cart) extends Event

  case class ItemRemoved(itemId: Any, cart: Cart) extends Event

  case object CartEmptied extends Event

  case object CartExpired extends Event

  case object CheckoutClosed extends Event

  case class CheckoutCancelled(cart: Cart) extends Event

  def props: Props = Props(new CartActor())

  case object CartTimerKey

}

class CartActor extends Timers {

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 10 seconds

  def restartTimer(): Unit = {
    timers.cancel(CartTimerKey)
    timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
  }

  def receive: Receive = empty

  def empty: Receive = {
    case AddItem(item) =>
      restartTimer()
      context become nonEmpty(Cart.empty.addItem(item))

    case GetItems =>
      sender() ! Cart.empty
  }

  def nonEmpty(cart: Cart): Receive = {
    case AddItem(item) =>
      restartTimer()
      context become nonEmpty(cart.addItem(item))

    case RemoveItem(item) if cart.size > 1 && cart.contains(item) =>
      restartTimer()
      context become nonEmpty(cart.removeItem(item))

    case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
      timers.cancel(CartTimerKey)
      context become empty

    case StartCheckout =>
      timers.cancel(CartTimerKey)
      val checkoutActor = context.actorOf(Checkout.props(self), "checkoutActor")
      sender() ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
      context become inCheckout(cart)

    case ExpireCart =>
      println("[CartActor] Cart expired")
      timers.cancel(CartTimerKey)
      context become empty

    case GetItems =>
      sender() ! cart
  }

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutClosed =>
      context become empty

    case ConfirmCheckoutCancelled =>
      restartTimer()
      context become nonEmpty(cart)

    case GetItems =>
      sender() ! cart
  }
}
