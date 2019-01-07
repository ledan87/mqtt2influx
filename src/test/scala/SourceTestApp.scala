package de.daniel.mqtt2brematic

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.MqttSource
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings}
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object SourceTestApp extends App {
  val connectionSettings = MqttConnectionSettings(
    "tcp://192.168.1.100:1883",
    "test-scala-client",
    new MemoryPersistence
  )
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val sourceSettings = connectionSettings.withClientId(clientId = "source-spec/source")

  val settings = MqttSourceSettings(sourceSettings, Map(
    "SENSORS/#" -> MqttQoS.AtLeastOnce))


  val processMessage: MqttMessage => Unit = msg => {
    val value = msg.payload.utf8String.toDouble

    val entity = getEntity(msg.topic, value).map(target => HttpEntity(ByteString(target)))

    if (entity.isDefined) {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = "http://192.168.1.100:8086/write?db=openhab_db", entity = entity.get))

      responseFuture
        .onComplete {
          case Success(res) => println(res)
          case Failure(_) => sys.error("something wrong")
        }
      println(s"[${msg.topic}]  $value")

    }

  }

  val sink: Sink[MqttMessage, Future[Done]] = Sink.foreach(processMessage)

  val (subscribed, result) = MqttSource
    .atMostOnce(settings, 8)
    .toMat(sink)(Keep.both)
    .run()

  def getEntity(topic: String, value: Double): Option[String] = {
    val pattern = new Regex("SENSORS/(.+)/(.+)/(.+)", "location", "device", "type")
    pattern.findFirstMatchIn(topic).map(result =>
      s"${result.group("location")}_${result.group("type")} value=$value")
  }
}
