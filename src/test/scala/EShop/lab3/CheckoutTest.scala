package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Checkout.props(cartActor.ref))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    orderManagerActor.send(checkoutActor, Checkout.SelectPayment("bbb"))
    cartActor.send(checkoutActor, Checkout.ConfirmPaymentReceived)
    cartActor.expectMsg(CartActor.ConfirmCheckoutClosed)
  }

  it should "Send cancel confirmation to cart after cancel on delivery select" in {
    val cartActor = TestProbe()
    val checkoutActor = system.actorOf(Checkout.props(cartActor.ref))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    cartActor.send(checkoutActor, Checkout.CancelCheckout)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "send cancel confirmation to cart after cancel on payment select" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Checkout.props(cartActor.ref))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    cartActor.send(checkoutActor, Checkout.CancelCheckout)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "send cancel confirmation to cart after cancel during processing payment" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Checkout.props(cartActor.ref))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    orderManagerActor.send(checkoutActor, Checkout.SelectPayment("bbb"))
    cartActor.send(checkoutActor, Checkout.CancelCheckout)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "send cancel confirmation after time expired on selecting delivery method" in {
    val cartActor = TestProbe()
    val checkoutActor = system.actorOf(Props(new Checkout(cartActor.ref) {
      override val checkoutTimerDuration: FiniteDuration = 1 seconds
    }))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    Thread.sleep(1500)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "send cancel confirmation after time expired on selecting payment method" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Props(new Checkout(cartActor.ref) {
      override val checkoutTimerDuration: FiniteDuration = 1 seconds
    }))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    Thread.sleep(1500)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "send cancel confirmation after time expired during processing payment" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Props(new Checkout(cartActor.ref) {
      override val paymentTimerDuration: FiniteDuration = 1 seconds
    }))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    orderManagerActor.send(checkoutActor, Checkout.SelectPayment("bbb"))
    Thread.sleep(1500)
    cartActor.expectMsg(CartActor.ConfirmCheckoutCancelled)
  }

  it should "do nothing after sending cancel when payment is realized" in {
    val cartActor = TestProbe()
    val orderManagerActor = TestProbe()
    val checkoutActor = system.actorOf(Checkout.props(cartActor.ref))

    cartActor.send(checkoutActor, Checkout.StartCheckout)
    orderManagerActor.send(checkoutActor, Checkout.SelectDeliveryMethod("aaa"))
    orderManagerActor.send(checkoutActor, Checkout.SelectPayment("bbb"))
    cartActor.send(checkoutActor, Checkout.ConfirmPaymentReceived)
    cartActor.expectMsg(CartActor.ConfirmCheckoutClosed)

    cartActor.send(checkoutActor, Checkout.CancelCheckout)
    cartActor.expectNoMessage()
  }
}
