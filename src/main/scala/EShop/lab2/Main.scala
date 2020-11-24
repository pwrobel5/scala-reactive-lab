package EShop.lab2

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn.{readInt, readLine}

object Main {
  def runClassicSystem(): Unit = {
    val actorSystem = ActorSystem("EShopClassic")
    val cartActor = actorSystem.actorOf(Props[CartActor], "classicCart")

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
          cartActor ! CartActor.AddItem(item)
        }
      }

      else if (clientInput.startsWith("RemoveItem")) {
        val splitted = clientInput.split(" ")

        if (splitted.length < 2)
          println("Incorrect command")
        else {
          val item = splitted(1)
          cartActor ! CartActor.RemoveItem(item)
        }
      }

      else if (clientInput.startsWith("Checkout")) {
        cartActor ! CartActor.StartCheckout
        val checkoutActor = actorSystem.actorOf(Props[Checkout], "classicCheckout")
        checkoutActor ! Checkout.StartCheckout

        print("Enter delivery method (or cancel): ")
        val deliveryMethod = readLine().trim()

        if (deliveryMethod.startsWith("cancel")) {
          checkoutActor ! Checkout.CancelCheckout
          cartActor ! CartActor.ConfirmCheckoutCancelled
        }

        else {
          checkoutActor ! Checkout.SelectDeliveryMethod(deliveryMethod)
          print("Select payment method (or cancel): ")
          val paymentMethod = readLine().trim()

          if (paymentMethod.startsWith("cancel")) {
            checkoutActor ! Checkout.CancelCheckout
            cartActor ! CartActor.ConfirmCheckoutCancelled
          }

          else {
            checkoutActor ! Checkout.SelectPayment(paymentMethod)
            print("Do you wish to pay? [y/n]: ")
            val decision = readLine().trim()

            if (decision.startsWith("y")) {
              checkoutActor ! Checkout.ConfirmPaymentReceived
              cartActor ! CartActor.ConfirmCheckoutClosed
            }

            else {
              checkoutActor ! Checkout.CancelCheckout
              cartActor ! CartActor.ConfirmCheckoutCancelled
            }
          }
        }
      }

      else if (clientInput.startsWith("Quit")) {
        endWorking = true
        actorSystem.terminate()
      }

      else {
        println("Unrecognized command")
      }
    }

    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }

  def runTypedSystem(): Unit = {
    val actorSystem = akka.actor.typed.ActorSystem(TypedActorsMain(), "EShopTyped")

    actorSystem ! TypedActorsMain.Init

    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }

  def main(args: Array[String]): Unit = {
    println("Choose actor type:\n\t1 - classic actors\n\t2 - typed actors")
    val actorChoose = readInt()

    if (actorChoose == 1) {
      println("Classic chosen")
      runClassicSystem()
    } else if (actorChoose == 2) {
      println("Typed chosen")
      runTypedSystem()
    } else
      println("Unrecognized type")
  }
}
