package com.netaporter.precanned

import org.scalatest.{ BeforeAndAfterAll, Matchers, BeforeAndAfter, FlatSpecLike, OptionValues }
import dsl.basic._
import spray.client.pipelining._
import spray.http.HttpEntity
import scala.concurrent.Await
import spray.http.StatusCodes._

class BasicDslSpec
    extends FlatSpecLike
    with Matchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with OptionValues
    with BaseSpec {

  val animalApi = httpServerMock(system).bind(8765).block

  after { animalApi.clearExpecations }
  override def afterAll() { system.shutdown() }

  "path expectation" should "match path" in {
    animalApi.expect(path("/animals"))
      .andRespondWith(resource("/responses/animals.json"))

    val resF = pipeline(Get("http://127.0.0.1:8765/animals"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""[{"name": "rhino"}, {"name": "giraffe"}, {"name": "tiger"}]""")
  }

  "several expectation" should "work together" in {
    animalApi.expect(get, path("/animals"), query("name" -> "giraffe"))
      .andRespondWith(resource("/responses/giraffe.json"))

    val resF = pipeline(Get("http://127.0.0.1:8765/animals?name=giraffe"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""{"name": "giraffe"}""")
  }

  "earlier expectations" should "take precedence" in {
    animalApi.expect(get, path("/animals"))
      .andRespondWith(resource("/responses/animals.json"))

    animalApi.expect(get, path("/animals"), query("name" -> "giraffe"))
      .andRespondWith(resource("/responses/giraffe.json"))

    val resF = pipeline(Get("http://127.0.0.1:8765/animals?name=giraffe"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""[{"name": "rhino"}, {"name": "giraffe"}, {"name": "tiger"}]""")
  }

  "unmatched requests" should "return 404" in {
    animalApi.expect(get, path("/animals")).andRespondWith(resource("/responses/animals.json"))

    val resF = pipeline(Get("http://127.0.0.1:8765/hotdogs"))
    val res = Await.result(resF, dur)

    res.status should equal(NotFound)
  }

  "custom status code with entity" should "return as expected" in {

    animalApi.expect(get, path("/animals")).andRespondWith(status(404), entity(HttpEntity("""{"error": "animals not found"}""")))

    val resF = pipeline(Get("http://127.0.0.1:8765/animals"))
    val res = Await.result(resF, dur)

    res.status should equal(NotFound)
    res.entity.toOption.value.asString should equal("""{"error": "animals not found"}""")
  }
}
