package com.spredfast.solrlorde

import java.util.concurrent.Executors
import javax.ws.rs._
import javax.ws.rs.core.Response.Status
import javax.ws.rs.core.{Context, Response, UriInfo}

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.massrelevance.dropwizard.ScalaApplication
import com.massrelevance.dropwizard.bundles.ScalaBundle
import io.dropwizard.Configuration
import io.dropwizard.setup.{Bootstrap, Environment}
import org.slf4j.LoggerFactory

@Path("/")
class SolrLordeResource(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)
  val executor = Executors.newFixedThreadPool(32)
  val manager = new ClusterStateManager(metrics)

  @GET
  @Path("{path:v1/.*}")
  def get(@Context uriInfo: UriInfo): Response = {
    val requestUri = uriInfo.getRequestUri
    val queryUri = requestUri.getRawPath + (if (requestUri.getRawQuery == null) "" else "?" + requestUri.getRawQuery)
//    val response = igProxy.get(RequestItem("GET", queryUri))
//
//    Response
//      .status(response.status)
//      .entity(response.body)
//      .`type`(response.contentType)
//      .build()
    Response.status(Status.OK).build()
  }
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