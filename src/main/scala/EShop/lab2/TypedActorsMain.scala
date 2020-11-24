package EShop.lab2

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.io.StdIn.readLine

object TypedActorsMain {

  trait TypedActors

  case object Init extends TypedActors

  def apply(): Behavior[TypedActors] = Behaviors.setup(context => {
    Behaviors.receiveMessage {
      case Init =>
        val cartActor = context.spawn(TypedCartActor(), "typedCart")
        var endWorking = false

        while (!endWorking) {
          println("Available options:\n\tAddItem name - add item to cart\n\tRemoveItem name - remove item from cart\n" +
            "\tCheckout - go to checkout\n\tQuit - quit")

          val clientInput = readLine().trim()

          if (clientInput.startsWith("AddItem")) {
            val splitted = clientInput.split(" ")

            if (splitted.length < 2)
              println("Incorrect command")
            else {
              val item = splitted(1)
              cartActor ! TypedCartActor.AddItem(item)
            }
          }

          else if (clientInput.startsWith("RemoveItem")) {
            val splitted = clientInput.split(" ")

            if (splitted.length < 2)
              println("Incorrect command")
            else {
              val item = splitted(1)
              cartActor ! TypedCartActor.RemoveItem(item)
            }
          }

          else if (clientInput.startsWith("Checkout")) {
            cartActor ! TypedCartActor.StartCheckout
            val checkoutActor = context.spawn(TypedCheckout(), "typedCheckout")
            checkoutActor ! TypedCheckout.StartCheckout

            print("Enter delivery method (or cancel): ")
            val deliveryMethod = readLine().trim()

            if (deliveryMethod.startsWith("cancel")) {
              checkoutActor ! TypedCheckout.CancelCheckout
              cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            }

            else {
              checkoutActor ! TypedCheckout.SelectDeliveryMethod(deliveryMethod)
              print("Select payment method (or cancel): ")
              val paymentMethod = readLine().trim()

              if (paymentMethod.startsWith("cancel")) {
                checkoutActor ! TypedCheckout.CancelCheckout
                cartActor ! TypedCartActor.ConfirmCheckoutCancelled
              }

              else {
                checkoutActor ! TypedCheckout.SelectPayment(paymentMethod)
                print("Do you wish to pay? [y/n]: ")
                val decision = readLine().trim()

                if (decision.startsWith("y")) {
                  checkoutActor ! TypedCheckout.ConfirmPaymentReceived
                  cartActor ! TypedCartActor.ConfirmCheckoutClosed
                }

                else {
                  checkoutActor ! TypedCheckout.CancelCheckout
                  cartActor ! TypedCartActor.ConfirmCheckoutCancelled
                }
              }
            }
          }

          else if (clientInput.startsWith("Quit")) {
            endWorking = true
          }

          else {
            println("Unrecognized command")
          }
        }

        Behaviors.stopped
    }
  }
  )
}
