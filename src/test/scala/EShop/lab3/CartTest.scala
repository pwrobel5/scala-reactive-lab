package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class CartTest
  extends TestKit(ActorSystem("CartTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  import CartActor._

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  //use GetItems command which was added to make test easier
  it should "be empty after creation" in {
    val cartActor = TestActorRef[CartActor]

    cartActor ! GetItems
    expectMsg(Cart.empty)
  }

  it should "add item properly" in {
    val cartActor = TestActorRef[CartActor]

    cartActor ! AddItem("abcd")
    cartActor ! GetItems
    expectMsg(Cart.empty.addItem("abcd"))
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor = TestActorRef[CartActor]

    cartActor ! AddItem("abcd")
    cartActor ! RemoveItem("abcd")

    cartActor ! GetItems
    expectMsg(Cart.empty)
  }

  it should "not change after deletion of non-existing item" in {
    val cartActor = TestActorRef[CartActor]

    cartActor ! AddItem("abcd")
    cartActor ! RemoveItem("efgh")

    cartActor ! GetItems
    expectMsg(Cart.empty.addItem("abcd"))
  }

  it should "start checkout" in {
    val cartActor = system.actorOf(Props[CartActor])

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout
    expectMsgType[OrderManager.ConfirmCheckoutStarted]
  }

  it should "be empty after closed checkout" in {
    val cartActor = system.actorOf(Props[CartActor])

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout
    expectMsgType[OrderManager.ConfirmCheckoutStarted]
    cartActor ! ConfirmCheckoutClosed
    cartActor ! GetItems
    expectMsg(Cart.empty)
  }

  it should "be non-empty after cancelled checkout" in {
    val cartActor = system.actorOf(Props[CartActor])

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout
    expectMsgType[OrderManager.ConfirmCheckoutStarted]
    cartActor ! ConfirmCheckoutCancelled
    cartActor ! GetItems
    expectMsg(Cart.empty.addItem("abcd"))
  }

  it should "be empty after timeout" in {
    val cartActor = system.actorOf(Props(new CartActor {
      override val cartTimerDuration: FiniteDuration = 1 seconds
    }))

    cartActor ! AddItem("abcd")
    Thread.sleep(1500)
    cartActor ! GetItems
    expectMsg(Cart.empty)
  }

  it should "not change items when in checkout state" in {
    val cartActor = system.actorOf(Props[CartActor])

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout
    expectMsgType[OrderManager.ConfirmCheckoutStarted]
    cartActor ! AddItem("efgh")
    cartActor ! GetItems
    expectMsg(Cart.empty.addItem("abcd"))
  }
}
