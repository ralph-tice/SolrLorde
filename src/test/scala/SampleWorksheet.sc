import org.apache.solr.client.solrj.impl.LBHttpSolrClient
import org.apache.solr.client.solrj.request.CollectionAdminRequest.ClusterStatus
import org.apache.solr.common.util.SimpleOrderedMap
import scalaj.http.Http

// val lines = scala.io.Source.fromFile("src/test/resources/clusterstate.json").mkString
val source = scala.io.Source.fromFile("/Users/ralphtice/personal/SolrLorde/src/test/resources/clusterstate.json")(scala.io.Codec.UTF8)
val bytes = source map(_.toByte) toArray
val solrSeed = "localhost:8990"
val rawClusterState = Http(s"http://${solrSeed}/solr/admin/collections").param("action", "clusterstatus").param("wt", "json").asBytes
val server = new LBHttpSolrClient(s"http://${solrSeed}/solr")
val request = new ClusterStatus()
val response= server.request(request)
val myCollection = "intelligence-facebook-1mo"
System.setProperty("solrSeed", "localhost:8990")
val rawState = response.get("cluster").asInstanceOf[SimpleOrderedMap[String]]
val collections = rawState.get("collections").asInstanceOf[SimpleOrderedMap[String]]
//val state = JSONClusterStateFactory.buildClusterState()

