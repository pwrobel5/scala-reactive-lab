package EShop.lab6

import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class ProductCatalogTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl("http://localhost:9000")
    .acceptCharsetHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn: ScenarioBuilder = scenario("BasicSimulation")
    .exec(
      http("work_basic")
        .post("/search")
        .body(StringBody("""{ "brand": "gerber", "productKeyWords": ["cream"]}"""))
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(rampUsers(5000).during(1.minutes))
  ).protocols(httpProtocol)
}

/*
class HttpWorkerGatlingTest extends Simulation {

  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9001", "http://localhost:9002", "http://localhost:9003")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(jsonFile(classOf[HttpWorkerGatlingTest].getResource("/data/work_data.json").getPath).random)
    .exec(
      http("work_basic")
        .post("/work")
        .body(StringBody("""{ "work": "${work}" }"""))
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(rampUsers(7).during(1.minutes))
  ).protocols(httpProtocol)
}*/