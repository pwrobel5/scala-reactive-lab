package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn.{readInt, readLine}
import scala.language.postfixOps

object Main {
  def runClassicSystem(): Unit = {
    val actorSystem = ActorSystem("EShopClassic")
    val orderManager = actorSystem.actorOf(Props[OrderManager], "orderManager")
    implicit val askTimeout: Timeout = Timeout(10 seconds)

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
          orderManager ! OrderManager.AddItem(item)
        }
      }

      else if (clientInput.startsWith("RemoveItem")) {
        val splitted = clientInput.split(" ")

        if (splitted.length < 2)
          println("Incorrect command")
        else {
          val item = splitted(1)
          orderManager ! OrderManager.RemoveItem(item)
        }
      }

      else if (clientInput.startsWith("Checkout")) {
        orderManager ! OrderManager.Buy

        print("Enter delivery method: ")
        val deliveryMethod = readLine().trim()
        print("Select payment method: ")
        val paymentMethod = readLine().trim()

        orderManager ! OrderManager.SelectDeliveryAndPaymentMethod(deliveryMethod, paymentMethod)
        print("Do you wish to pay? [y/n]: ")
        val decision = readLine().trim()

        if (decision.startsWith("y")) {
          orderManager ! OrderManager.Pay
          val response = orderManager ? OrderManager.Pay
          val result = Await.result(response, askTimeout.duration)
          val resultString = result.asInstanceOf[String]
          println(resultString)
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
