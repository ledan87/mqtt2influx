package de.daniel.mqtt2brematic

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.{MqttMessage, MqttQoS}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import de.daniel.mqtt2brematic.actor.MqttActor
import de.daniel.mqtt2brematic.actor.MqttActor.MqttListener
import de.daniel.mqtt2brematic.resources.MqttDiscovery

import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object Mqtt2Influx extends App {

  implicit val system = ActorSystem.create("Mqtt2Influx")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val mqttListener = new MqttListener {

    override def sink: Sink[MqttMessage, Future[Done]] = Sink.foreach(part)

    override def topics: Map[String, MqttQoS] = Map(
      "SENSORS/#" -> MqttQoS.AtLeastOnce)

    override def name: String = "Mqtt2Influx"
  }

  val mqtt = system.actorOf(MqttActor.props(mqttListener, new MqttDiscovery), "mqtt")


  val part: MqttMessage => Unit = msg => {
    val pattern = new Regex("SENSORS/miflora-mqtt-daemon/.*")
    if (!pattern.findFirstMatchIn(msg.topic).isDefined) {
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
  }

  def getEntity(topic: String, value: Double): Option[String] = {
    val pattern = new Regex("SENSORS/(.+)/(.+)/(.+)", "location", "device", "type")
    pattern.findFirstMatchIn(topic).map(result =>
      s"${result.group("location")}_${result.group("type")} value=$value")
  }
}
