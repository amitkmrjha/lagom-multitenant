package com.example.helloworld.impl.projection.publish

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.cassandra.internal.TenantCassandraProjection
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.example.helloworld.api.HelloWorldService
import com.example.helloworld.impl.HelloWorldEvent
import com.example.helloworld.impl.tenant.TenantPersistencePlugin
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

object HelloWorldPublishProjection {

  def init(system: ActorSystem[_],
           tenantPlugins: Seq[TenantPersistencePlugin]): Unit = {
    val sendProducer = createProducer(system)
    val topic = HelloWorldService.TOPIC_NAME

    tenantPlugins.map { p =>
      //val topic = system.settings.config.getString("shopping-cart-service.kafka.topic")

      ShardedDaemonProcess(system).init(
        name = s"HelloWorldPublishProjection-${p.tenantPersistenceId.tenantId}",
        HelloWorldEvent.Tag.allTags.size,
        index =>
          ProjectionBehavior(
            createProjectionFor(system, topic, sendProducer, index,p)),
        ShardedDaemonProcessSettings(system),
        Some(ProjectionBehavior.Stop))
    }
  }

  private def createProducer(system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer = SendProducer(producerSettings)(system)
    CoordinatedShutdown(system).addTask(
      CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
      "close-sendProducer") { () =>
      sendProducer.close()
    }
    sendProducer
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   topic: String,
                                   sendProducer: SendProducer[String, Array[Byte]],
                                   index: Int,
                                   tenantPlugin: TenantPersistencePlugin)
  : AtLeastOnceProjection[Offset, EventEnvelope[HelloWorldEvent]] = {
    val tag = HelloWorldEvent.Tag.allTags.toSeq(index).tag

    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[HelloWorldEvent]] =
      EventSourcedProvider.eventsByTag[HelloWorldEvent](
        system = system,
        readJournalPluginId = tenantPlugin.queryJournalPlugin.pluginId,
        tag = tag)

    TenantCassandraProjection.atLeastOnce(
      projectionId = ProjectionId("PublishEventsProjection", tag),
      sourceProvider,
      handler =
        () => new HelloWorldPublishHandler(system, topic, sendProducer),
      tenantPlugin.projectionPlugin.pluginId
    )
  }

}
