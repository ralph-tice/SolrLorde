name := "SolrLorde"

version := "1.0"

scalaVersion := "2.11.6"

val json4sVersion = "3.2.11"

libraryDependencies += "com.massrelevance" %% "dropwizard-scala" % "0.7.1"
libraryDependencies += "org.apache.solr" % "solr-solrj" % "5.0.0"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "1.1.4"
libraryDependencies += "org.json4s" %% "json4s-native" % json4sVersion
libraryDependencies += "org.json4s" %% "json4s-jackson" % json4sVersion