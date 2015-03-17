package com.spredfast.solrlorde

import java.util.concurrent.Executors

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.massrelevance.dropwizard.ScalaApplication
import com.massrelevance.dropwizard.bundles.ScalaBundle
import io.dropwizard.Configuration
import io.dropwizard.setup.{Bootstrap, Environment}
import org.slf4j.LoggerFactory
import javax.ws.rs._

@Path("/")
class SolrLordeResource(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)
  val executor = Executors.newFixedThreadPool(32)
}

object SolrLordeService extends ScalaApplication[SolrLordeConfig] {
  override def getName = "example"

  def initialize(bootstrap: Bootstrap[SolrLordeConfig]) {
    bootstrap.addBundle(new ScalaBundle)
  }

  def run(configuration: SolrLordeConfig, env: Environment) {
    env.healthChecks().register("fake", new FakeHealthCheck)
    env.jersey().register(new SolrLordeResource(env.metrics()))
  }
}

// Shut Dropwizard up
class FakeHealthCheck extends HealthCheck {
  def check() = {
    HealthCheck.Result.healthy()
  }
}

class SolrLordeConfig extends Configuration