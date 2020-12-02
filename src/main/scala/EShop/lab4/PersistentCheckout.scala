package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.{ActorRef, Props, TimerScheduler, Timers}
import akka.event.Logging
import akka.persistence.PersistentActor

import scala.concurrent.duration._
import scala.language.postfixOps

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String): Props =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
                          cartActor: ActorRef,
                          val persistenceId: String
                        ) extends Timers with PersistentActor {

  import EShop.lab2.Checkout._

  private val log = Logging(context.system, this)

  val timerDuration: FiniteDuration = 10 seconds

  def startCheckoutTimer(timer: TimerScheduler): Unit =
    timer.startSingleTimer(CheckoutTimerKey, ExpireCheckout, timerDuration)

  def stopCheckoutTimer(timer: TimerScheduler): Unit =
    timer.cancel(CheckoutTimerKey)

  def restartCheckoutTimer(timer: TimerScheduler): Unit = {
    stopCheckoutTimer(timer)
    startCheckoutTimer(timer)
  }

  def startPaymentTimer(timer: TimerScheduler): Unit =
    timer.startSingleTimer(PaymentTimerKey, ExpirePayment, timerDuration)

  def stopPaymentTimer(timer: TimerScheduler): Unit =
    timer.cancel(PaymentTimerKey)

  def restartPaymentTimer(timer: TimerScheduler): Unit = {
    stopPaymentTimer(timer)
    startPaymentTimer(timer)
  }

  private def updateState(event: Event, maybeTimer: Option[TimerScheduler] = None): Unit = {
    context.become(
      event match {
        case CheckoutStarted =>
          val currentTimer = maybeTimer.getOrElse(timers)
          startCheckoutTimer(currentTimer)
          selectingDelivery(currentTimer)

        case DeliveryMethodSelected(method) =>
          val currentTimer = maybeTimer.getOrElse(timers)
          restartCheckoutTimer(currentTimer)
          selectingPaymentMethod(currentTimer)

        case CheckOutClosed => closed

        case CheckoutCancelled => cancelled

        case PaymentStarted(payment) =>
          val currentTimer = maybeTimer.getOrElse(timers)
          stopCheckoutTimer(currentTimer)
          startPaymentTimer(currentTimer)
          processingPayment(currentTimer)
      }
    )
  }

  def receiveCommand: Receive = {
    case StartCheckout =>
      persist(CheckoutStarted) {
        event => updateState(event)
      }
  }

  def selectingDelivery(timer: TimerScheduler): Receive = {
    case SelectDeliveryMethod(deliveryMethod) =>
      persist(DeliveryMethodSelected(deliveryMethod)) {
        event => updateState(event, Option(timer))
      }

    case CancelCheckout | ExpireCheckout =>
      persist(CheckoutCancelled) {
        event => updateState(event, Option(timer))
      }
  }

  def selectingPaymentMethod(timer: TimerScheduler): Receive = {
    case SelectPayment(paymentMethod) =>
      val paymentActor = context.actorOf(Payment.props(paymentMethod, sender, self), "paymentActor")
      sender() ! OrderManager.ConfirmPaymentStarted(paymentActor)
      persist(PaymentStarted(paymentActor)) {
        event => updateState(event, Option(timer))
      }

    case CancelCheckout | ExpireCheckout =>
      persist(CheckoutCancelled) {
        event => updateState(event, Option(timer))
      }
  }

  def processingPayment(timer: TimerScheduler): Receive = {
    case ConfirmPaymentReceived =>
      persist(CheckOutClosed) {
        event => updateState(event, Option(timer))
      }

    case CancelCheckout | ExpirePayment =>
      persist(CheckoutCancelled) {
        event => updateState(event, Option(timer))
      }
  }

  def cancelled: Receive = {
    case _ =>
      cartActor ! CartActor.ConfirmCheckoutCancelled
      context.stop(self)
  }

  def closed: Receive = {
    case _ =>
      cartActor ! CartActor.ConfirmCheckoutClosed
      context.stop(self)
  }

  override def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case (event: Event, timer: TimerScheduler) => updateState(event, Option(timer))
  }
}
