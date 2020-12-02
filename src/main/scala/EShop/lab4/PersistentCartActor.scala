package EShop.lab4

import EShop.lab2.Cart
import EShop.lab3.OrderManager
import akka.actor.{Props, TimerScheduler, Timers}
import akka.event.Logging
import akka.persistence.PersistentActor

import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String): Props = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
                           val persistenceId: String
                         ) extends PersistentActor with Timers {

  import EShop.lab2.CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5.seconds

  def cancelTimer(timer: TimerScheduler): Unit = {
    timer.cancel(CartTimerKey)
  }

  def startTimer(timer: TimerScheduler): Unit = {
    timer.startSingleTimer(CartTimerKey, ExpireCart, cartTimerDuration)
  }

  def restartTimer(timer: TimerScheduler): Unit = {
    cancelTimer(timer)
    startTimer(timer)
  }

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[TimerScheduler] = None): Unit = {
    context.become(
      event match {
        case CartExpired | CheckoutClosed => empty

        case CheckoutCancelled(cart) =>
          val currentTimer = timer.getOrElse(timers)
          restartTimer(currentTimer)
          nonEmpty(cart, currentTimer)

        case ItemAdded(item, cart) =>
          val currentTimer = timer.getOrElse(timers)
          restartTimer(currentTimer)
          nonEmpty(cart.addItem(item), currentTimer)

        case CartEmptied =>
          cancelTimer(timer.getOrElse(timers))
          empty

        case ItemRemoved(item, cart) =>
          val currentTimer = timer.getOrElse(timers)
          restartTimer(currentTimer)
          nonEmpty(cart.removeItem(item), currentTimer)

        case CheckoutStarted(checkoutRef, cart) =>
          cancelTimer(timer.getOrElse(timers))
          inCheckout(cart)
      }
    )
  }

  def empty: Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event)
      }
  }

  def nonEmpty(cart: Cart, timer: TimerScheduler): Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, cart)) {
        event => updateState(event, Option(timer))
      }

    case RemoveItem(item) if cart.size > 1 && cart.contains(item) =>
      persist(ItemRemoved(item, cart)) {
        event => updateState(event, Option(timer))
      }

    case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
      persist(CartEmptied) {
        event => updateState(event, Option(timer))
      }

    case StartCheckout =>
      val checkoutActor = context.actorOf(PersistentCheckout.props(self, persistenceId + "_checkout"), "checkoutActor")
      persist(CheckoutStarted(checkoutActor, cart)) {
        event =>
          sender() ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
          updateState(event, Option(timer))
      }

    case ExpireCart =>
      persist(CartExpired) {
        event => updateState(event)
      }
  }

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutClosed =>
      persist(CheckoutClosed) {
        event => updateState(event)
      }

    case ConfirmCheckoutCancelled =>
      persist(CheckoutCancelled(cart)) {
        event => updateState(event)
      }
  }

  override def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case (event: Event, timer: TimerScheduler) => updateState(event, Option(timer))
  }
}
