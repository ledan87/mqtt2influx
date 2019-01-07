package de.daniel.mqtt2brematic.resources

import java.util.regex.Pattern

import net.straylightlabs.hola.dns.Domain
import net.straylightlabs.hola.sd.{Instance, Query, Service}

class MqttDiscovery {

  import de.daniel.mqtt2brematic.resources.MqttDiscovery._

  def discoverMqttInstances: List[String] = {
    import scala.collection.JavaConverters._
    val service = Service.fromName("_mqtt._tcp")
    val query = Query.createFor(service, Domain.LOCAL)
    query.runOnce().asScala.toList.map(instance => getHosts(instance)).flatten
  }

  private def getHosts(instance: Instance): Option[String] = {
    import scala.collection.JavaConverters._
    instance.getAddresses.asScala.map(_.getHostAddress)
      .find(IP4_PATTERN.matcher(_).matches())
      .map(host => s"$host:${instance.getPort}")
  }
}

object MqttDiscovery {
  private val IP4_PATTERN =
    Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")
}
