package de.daniel.mqtt2brematic.actor

import akka.actor.{Actor, ActorLogging, FSM, Props}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.MqttSource
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings}
import akka.stream.scaladsl.{Flow, Keep, Merge, Sink, Source}
import akka.{Done, NotUsed}
import de.daniel.mqtt2brematic.actor.MqttActor.{MqttListener, MqttState}
import de.daniel.mqtt2brematic.resources.MqttDiscovery
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future
import scala.concurrent.duration._

class MqttActor(mqttListener: MqttListener,
                mqttDiscovery: MqttDiscovery) extends Actor with ActorLogging with FSM[MqttState, NotUsed] {

  import MqttActor._

  startWith(NotConnected, NotUsed)

  when(NotConnected, stateTimeout = 1 second) {
    case Event(StateTimeout, _) => {
      goto(Connected)
    }
  }

  onTransition {
    case NotConnected -> Connected => {
      val hosts = mqttDiscovery.discoverMqttInstances
      hosts.foreach(log.info("Found Mqtt Host [{}]", _))
      if (hosts.isEmpty) {
        log.info("No hosts where found.")
        self ! LostConnection()
      } else {

        val source: Source[MqttMessage, NotUsed]
        = hosts.map(createMqttSource).reduceLeft((a, b) => Source.combine(a, b)(Merge(_)))
        implicit val materializer = ActorMaterializer()

        val logFlow = Flow[MqttMessage].map(msg => {
          log.info(s"Received Message on topic [${msg.topic}]: ${msg.payload.utf8String}")
          msg
        })
        val result = source.viaMat(logFlow)(Keep.left).toMat(mqttListener.sink)(Keep.right).run()
        import context.dispatcher
        result.failed.foreach(_ => {
          log.error("Lost connection to Mqtt-Broker. Will try to reconnect.")
          self ! LostConnection()
        })
        log.info("Connection to Mqtt-Broker established.")

      }
    }
    case ConnectionLost -> NotConnected => {
      log.info("Retry to connect to MQTT-Broker.")
    }
  }

  when(ConnectionLost, stateTimeout = 15 seconds) {
    case Event(StateTimeout, _) => goto(NotConnected)
  }

  when(Connected) {
    case Event(LostConnection(), _) => {
      goto(ConnectionLost)
    }
  }

  whenUnhandled {
    case a =>
      val send = sender()
      log.info("Unhandled message {} from {}", a, send)
      stay
  }

  initialize()


  private def createMqttSource(host: String): Source[MqttMessage, NotUsed] = {
    val connectionSettings = MqttConnectionSettings(
      s"tcp://$host",
      s"${mqttListener.name}",
      new MemoryPersistence
    )
    val settings = MqttSourceSettings(connectionSettings, mqttListener.topics)

    Source.combine(Source.empty,
      MqttSource
        .atMostOnce(settings, 8))(Merge(_))
  }
}

object MqttActor {

  def props(mqttListener: MqttListener,
            mqttDiscovery: MqttDiscovery): Props =
    Props(classOf[MqttActor], mqttListener, mqttDiscovery)

  sealed trait MqttState

  case object NotConnected extends MqttState

  case object Connected extends MqttState

  case object ConnectionLost extends MqttState

  case class LostConnection()

  case class ConnectToMqttBroker()

  trait MqttListener {
    def name: String
    def topics: Map[String, MqttQoS]
    def sink: Sink[MqttMessage, Future[Done]]
  }

}
