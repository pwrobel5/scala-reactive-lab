package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed
import akka.{actor => classic}

object Payment {

  sealed trait Command

  case object DoPayment extends Command

  def props(method: String, orderManager: classic.ActorRef, checkout: typed.ActorRef[TypedCheckout.Command]): classic.Props =
    classic.Props(new Payment(method, orderManager, checkout))
}

class Payment(
               method: String,
               orderManager: classic.ActorRef,
               checkout: typed.ActorRef[TypedCheckout.Command]
             ) extends classic.Actor {

  import Payment._

  override def receive: Receive = {
    case DoPayment =>
      sender ! OrderManager.ConfirmPaymentReceived
      checkout ! TypedCheckout.ConfirmPaymentReceived
      context.stop(self)
  }

}
