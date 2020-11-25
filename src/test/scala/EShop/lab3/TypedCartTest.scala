package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.{actor => classic}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.scaladsl.adapter._
import akka.testkit.TestProbe

class TypedCartTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "be empty after creation" in {
    val cartActor = testKit.spawn(TypedCartActor())
    val probeActor = testKit.createTestProbe[Cart]()

    cartActor ! GetItems(probeActor.ref)
    probeActor.expectMessage(Cart.empty)
  }

  it should "add item properly" in {
    val cartActor = testKit.spawn(TypedCartActor())
    val probeActor = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("abcd")
    cartActor ! GetItems(probeActor.ref)
    probeActor.expectMessage(Cart.empty.addItem("abcd"))
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor = testKit.spawn(TypedCartActor())
    val probeActor = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("abcd")
    cartActor ! RemoveItem("abcd")
    cartActor ! GetItems(probeActor.ref)
    probeActor.expectMessage(Cart.empty)
  }

  it should "not change after deletion of non-existing item" in {
    val cartActor = testKit.spawn(TypedCartActor())
    val probeActor = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("abcd")
    cartActor ! RemoveItem("efgh")
    cartActor ! GetItems(probeActor.ref)
    probeActor.expectMessage(Cart.empty.addItem("abcd"))
  }

  it should "start checkout" in {
    val classicSystem = classic.ActorSystem()
    val cartActor = classicSystem.spawn(TypedCartActor(), "cartActor")
    val orderManager = TestProbe()(classicSystem)

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout(orderManager.ref)
    orderManager.expectMsgType[OrderManager.ConfirmCheckoutStarted]

    classicSystem.terminate()
  }

  it should "be empty after closed checkout" in {
    val classicSystem = classic.ActorSystem()
    val cartActor = classicSystem.spawn(TypedCartActor(), "cartActor")
    val orderManager = TestProbe()(classicSystem)
    val probeActor = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("abcd")
    cartActor ! StartCheckout(orderManager.ref)
    orderManager.expectMsgType[OrderManager.ConfirmCheckoutStarted]
    cartActor ! ConfirmCheckoutClosed
    cartActor ! GetItems(probeActor.ref)
    probeActor.expectMessage(Cart.empty)

    classicSystem.terminate()
  }
}