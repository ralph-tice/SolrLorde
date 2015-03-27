import org.apache.solr.common.cloud._

// val lines = scala.io.Source.fromFile("src/test/resources/clusterstate.json").mkString
val source = scala.io.Source.fromFile("/Users/ralphtice/personal/SolrLorde/src/test/resources/clusterstate.json")(scala.io.Codec.UTF8)
val bytes = source map(_.toByte) toArray

def buildClusterState(bytes: Array[Byte]) {
  val stateMap = ZkStateReader.fromJSON(bytes).asInstanceOf[java.util.LinkedHashMap[String, Object]]
  val cluster = stateMap.get("cluster").asInstanceOf[java.util.LinkedHashMap[String, Object]]
  val collectionObjects = cluster.get("collections").asInstanceOf[java.util.LinkedHashMap[String, Object]]
  val version = 1
  val collections = new java.util.LinkedHashMap[String,DocCollection](collectionObjects.size())
  import scala.collection.JavaConversions._
  collectionObjects.entrySet().foreach(entry => {
    val collectionName = entry.getKey()
    val coll = collectionFromObjects(entry)
    collections.put(collectionName, coll)
  })

  val liveNodes = new java.util.TreeSet[String]()
  new ClusterState( version, liveNodes, collections)
}

def collectionFromObjects(collection: java.util.Map.Entry[String, Object], version: Integer = 1) : DocCollection = {
  val name : String = collection.getKey
  val collectionData = collection.getValue.asInstanceOf[java.util.Map[String, Object]]
  val sliceObjs = collectionData.get(DocCollection.SHARDS).asInstanceOf[java.util.Map[String, Object]]
  // breaking backcompat for 4.0 here
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
  if (genericSlices == null) return java.util.Collections.emptyMap().asInstanceOf[java.util.Map[String, Slice]]
  val result = new java.util.LinkedHashMap[String,Slice](genericSlices.size()).asInstanceOf[java.util.Map[String, Slice]]
  import scala.collection.JavaConversions._
  genericSlices.entrySet().foreach(entry => {
    val name = entry.getKey()
    val value = entry.getValue()
    result.put(name, value match {
      case slice: Slice => slice
      case valueMap: java.util.Map[String, Object] => new Slice(name, null, valueMap)
    })
  })
  result
}