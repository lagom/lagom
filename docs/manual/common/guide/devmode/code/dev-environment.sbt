//#start-elastic-search
import java.io.Closeable

val startElasticSearch = taskKey[Closeable]("Starts elastic search")

startElasticSearch in ThisBuild := {
  val esVersion     = "5.4.0"
  val log           = streams.value.log
  val elasticsearch = target.value / s"elasticsearch-$esVersion"

  if (!elasticsearch.exists()) {
    log.info(s"Downloading Elastic Search $esVersion...")
    IO.unzipURL(url(s"https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$esVersion.zip"), target.value)
    IO.append(elasticsearch / "config" / "log4j2.properties", "\nrootLogger.level = warn\n")
  }

  val binFile = if (sys.props("os.name") == "Windows") {
    elasticsearch / "bin" / "elasticsearch.bat"
  } else {
    elasticsearch / "bin" / "elasticsearch"
  }

  import scala.sys.process._ // if on sbt 0.13, don't import this
  val process = Process(binFile.getAbsolutePath, elasticsearch).run(log)
  log.info("Elastic search started on port 9200")

  new Closeable {
    override def close(): Unit = process.destroy()
  }
}
//#start-elastic-search

//#infrastructure-services
lagomInfrastructureServices in ThisBuild += (startElasticSearch in ThisBuild).taskValue
//#infrastructure-services
