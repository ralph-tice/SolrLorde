package com.spredfast.solrlorde

import com.codahale.metrics.MetricRegistry
import org.apache.solr.client.solrj.impl.{CloudSolrClient}
import org.apache.solr.common.cloud.{Replica, SolrZkClient}
import org.slf4j.LoggerFactory

case class SolrCloud(name: String)
case class SolrCollection(name: String)
case class ShardName(name: String)
case class SolrResponse(status: Int, body: String, contentType: String)
//case class SliceReference()

object SolrCollections {
  import scala.collection.JavaConverters._
  val zkString        = System.getProperty("zkString", null)
  val zkClient = new SolrZkClient(zkString, 10000)

  // TODO: we should put a watch here so we can see new collections
  val solrClouds = zkClient.getChildren("/", null, false).asScala.toList
  val solrCloudsStub = List("solrcloud1")

  val solrServers: Map[SolrCloud, CloudSolrClient] = solrClouds.map(x  => (SolrCloud(x), new CloudSolrClient(zkString + x))).toMap
}

class CollectionManager(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)

  def moveShard(solrCloudName: SolrCloud, shardToMove: ShardName, replicaFrom: org.apache.solr.common.cloud.Slice, replicaTo: Replica) {

  }
}

object ClusterStateManager {
  val CLOUD_NOT_FOUND_RESPONSE = SolrResponse(500, "Solr collection not found", "text/plain")
}

class ClusterStateManager(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)

  def health(solrCloudName: SolrCloud): SolrResponse = {
    val cloud = SolrCollections.solrServers.getOrElse(solrCloudName, None)
    cloud match {
      case None =>
        ClusterStateManager.CLOUD_NOT_FOUND_RESPONSE
      case server: CloudSolrClient =>
        val state = server.getZkStateReader.getClusterState
        val collections = state.getCollections
        SolrResponse(200, collectionsHealth(collections), "text/json")
    }
  }

  private def collectionsHealth(collections: java.util.Set[String]): String = {
    ""

  }
}