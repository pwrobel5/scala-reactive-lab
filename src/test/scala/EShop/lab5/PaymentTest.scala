package EShop.lab5

import EShop.lab3.Payment.DoPayment
import EShop.lab5.Payment.{PaymentConfirmed, PaymentRejected, PaymentRestarted}
import PaymentServiceServer.PaymentServiceServer
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaymentTest
  extends TestKit(ActorSystem("PaymentTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "properly confirm payment after 2 retries using payu payment method" in {
    val manager = TestProbe()
    val checkout = TestProbe()
    val payment = TestActorRef(Payment.props("payu", manager.ref, checkout.ref))

    val server = new PaymentServiceServer()
    Future {
      server.run()
    }

    payment ! DoPayment

    manager.expectMsg(PaymentRestarted)
    manager.expectMsg(PaymentRestarted)
    manager.expectMsg(PaymentConfirmed)
    server.system.terminate()
  }

  it should "reject when malformed URL exception occurred" in {
    val manager = TestProbe()
    val checkout = TestProbe()
    val payment = TestActorRef(new Payment("invalid_link", manager.ref, checkout.ref))

    payment ! DoPayment
    manager.expectMsg(PaymentRejected)
  }

  it should "restart when http timeout exception occurred" in {
    val manager = TestProbe()
    val checkout = TestProbe()
    val payment = TestActorRef(Payment.props("paypal", manager.ref, checkout.ref))

    payment ! DoPayment
    manager.expectMsg(PaymentRestarted)
  }

  it should "restart when socket exception occurred" in {
    val manager = TestProbe()
    val checkout = TestProbe()
    val payment = TestActorRef(Payment.props("socket_exception", manager.ref, checkout.ref))

    payment ! DoPayment
    manager.expectMsg(PaymentRestarted)
  }
}
