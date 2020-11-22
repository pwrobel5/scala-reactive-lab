package EShop.lab2

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

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props: Props = Props(new CartActor())

  case object CartTimerKey

}

class CartActor extends Timers {

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  def restartTimer(): Unit = {
    timers.cancel(CartTimerKey)
    timers.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
  }

  def receive: Receive = empty

  def empty: Receive = {
    case AddItem(item) =>
      restartTimer()
      context become nonEmpty(Cart.empty.addItem(item))
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
      context become inCheckout(cart)

    case ExpireCart =>
      timers.cancel(CartTimerKey)
      context become empty
  }

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutClosed =>
      context become empty

    case ConfirmCheckoutCancelled =>
      restartTimer()
      context become nonEmpty(cart)
  }
}
