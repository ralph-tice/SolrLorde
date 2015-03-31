package com.spredfast.solrlorde

import com.codahale.metrics.MetricRegistry
import org.apache.solr.client.solrj.impl.{CloudSolrClient, LBHttpSolrClient}
import org.apache.solr.client.solrj.request.CollectionAdminRequest.ClusterStatus
import org.apache.solr.common.cloud._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scalaj.http.Http

case class SolrCollection(name: String)
case class ShardName(name: String)
case class SolrResponse(status: Int, body: String, contentType: String)

object ZKSolrCollections {

  val zkString = sys.props.get("zkString") match {
    case Some(x) => x
    case None => throw new Exception("zkString is required property for ZK-based management")
  }
  val zkClient = new SolrZkClient(zkString, 10000)

  // TODO: we should put a watch here so we can see new collections
  val solrClouds = zkClient.getChildren("/", null, false).asScala.toList

  val solrServers: Map[SolrCollection, CloudSolrClient] = solrClouds.map(x  => (SolrCollection(x), new CloudSolrClient(zkString + x))).toMap
}
object JSONClusterStateFactory {
  // These exist here because the SolrJ code expects data via ZK instead of JSON
  // reimplementation from https://github.com/apache/lucene-solr/blob/lucene_solr_4_10_4/solr/solrj/src/java/org/apache/solr/common/cloud/ClusterState.java#L273-L288
  def buildClusterState(bytes: Array[Byte]) : ClusterState = {
    val stateMap = ZkStateReader.fromJSON(bytes).asInstanceOf[java.util.LinkedHashMap[String, Object]]
    val cluster = stateMap.get("cluster").asInstanceOf[java.util.LinkedHashMap[String, Object]]
    val collectionObjects = cluster.get("collections").asInstanceOf[java.util.LinkedHashMap[String, Object]]
    val version = 1 // maybe timestamp instead?

    val collections = new java.util.LinkedHashMap[String,DocCollection](collectionObjects.size())

    collectionObjects.entrySet().asScala.foreach(entry => {
      val collectionName = entry.getKey()
      val coll = collectionFromObjects(entry)
      collections.put(collectionName, coll)
    })

    val liveNodes : java.util.Set[String] = new java.util.HashSet[String]

    val nodes = stateMap.get("cluster").asInstanceOf[java.util.LinkedHashMap[String, Object]].get("live_nodes").asInstanceOf[java.util.ArrayList[String]].asScala

    nodes.foreach(node => { liveNodes.add(node) })

    new ClusterState( version, liveNodes, collections)
  }

  def collectionFromObjects(collection: java.util.Map.Entry[String, Object], version: Integer = 1) : DocCollection = {
    val name : String = collection.getKey
    val collectionData = collection.getValue.asInstanceOf[java.util.Map[String, Object]]
    val sliceObjs = collectionData.get(DocCollection.SHARDS).asInstanceOf[java.util.Map[String, Object]]
    // breaking backcompat for 4.0 here, original comment below
    //// legacy format from 4.0... there was no separate "shards" level to contain the collection shards.
    val slices = makeSlices(sliceObjs)
    val props = sliceObjs match {
      case _ : Any => {
        collectionData.remove(DocCollection.SHARDS)
        collectionData.asInstanceOf[java.util.Map[String, Object]]
      }
      case _ => java.util.Collections.emptyMap().asInstanceOf[java.util.Map[String, Object]]
    }

    val router = props.get(DocCollection.DOC_ROUTER) match {
      case routerStr : String => DocRouter.getDocRouter(routerStr)
      case routerMap : java.util.Map[String, Object] => DocRouter.getDocRouter(routerMap.get("name").asInstanceOf[String])
      case _ => DocRouter.DEFAULT
    }

    new DocCollection(name, slices, props, router)
  }

  def makeSlices(genericSlices: java.util.Map[String,Object]) : java.util.Map[String, Slice] = {
    genericSlices match {
      // slices will be None if the collection is empty
      case null => java.util.Collections.emptyMap().asInstanceOf[java.util.Map[String, Slice]]
      case slices => {
        val result = new java.util.LinkedHashMap[String,Slice](slices.size()).asInstanceOf[java.util.Map[String, Slice]]

        slices.entrySet().asScala.foreach(entry => {
          val name = entry.getKey()
          val value = entry.getValue()
          result.put(name, value match {
            case slice: Slice => slice
            case valueMap: java.util.Map[String, Object] => new Slice(name, null, valueMap)
          })
        })
        result
      }
    }
  }
}
object JSONSolrCollections {

  val solrSeed = sys.props.get("solrSeed") match {
    case Some(x) => x
    case None => throw new Exception ("solrSeed is required for JSON-based management, ex: my-solr-server:8983")
  }

  val rawClusterState = Http(s"http://${solrSeed}/solr/admin/collections").param("action", "clusterstatus").param("wt", "json").asBytes
  val clusterState = JSONClusterStateFactory.buildClusterState(rawClusterState.body)


  val solrClient = new LBHttpSolrClient()
  val solrServers: LBHttpSolrClient = {
    clusterState.getLiveNodes.asScala.foreach(x => { solrClient.addSolrServer(x)})
    solrClient
  }
}
class JSONSolrCollections() {

}

class CollectionManager(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)

  def moveShard(solrCollectionName: SolrCollection, shardToMove: ShardName, replicaFrom: org.apache.solr.common.cloud.Slice, replicaTo: Replica) {

  }
}

object ClusterStateManager {
  val CLOUD_NOT_FOUND_RESPONSE = SolrResponse(500, "Solr collection not found", "text/plain")
}

class ClusterStateManager(metrics: MetricRegistry) {
  val logger = LoggerFactory.getLogger(this.getClass)
  // TODO: implement toggle flag to switch between JSON/ZK based management
  val collections = JSONSolrCollections

  def health(solrCollectionName: SolrCollection): SolrResponse = {
    // TODO: maybe try to unify this accessor at some point so ZK/JSON management isn't bifurcated
    collections.solrServers match {
      case server: CloudSolrClient =>
        val state = server.getZkStateReader.getClusterState
        val collections = state.getCollections
        SolrResponse(200, collectionsHealth(collections), "text/json")
      case server: LBHttpSolrClient =>
        val state = server.request(new ClusterStatus)
        val collections = state.get("") // collections
        SolrResponse(200, collectionsHealth(null), "text/json")
      case _ => ClusterStateManager.CLOUD_NOT_FOUND_RESPONSE
    }
    // val response = Http("http://localhost:8990/solr/admin/collections").param("action", "CLUSTERSTATUS").param("wt", "json").asString
  }

  private def collectionsHealth(collections: java.util.Set[String]): String = {
    ""
    //val something = Json("")
  }
}
