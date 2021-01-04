package com.example.helloworld.impl.projection

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.projection.scaladsl.SourceProvider
import com.example.helloworld.impl.HelloWorldEvent

object HelloWorldProjection {
  def init(
            system: ActorSystem[_]
            /*repository: ItemPopularityRepository*/): Unit = {

    CassandraProjection.createOffsetTableIfNotExists()(system)

    ShardedDaemonProcess(system).init(
      name = "HelloWorldProjection",
      HelloWorldEvent.Tag.allTags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system,index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }


  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   /*repository: ItemPopularityRepository,*/
                                   index: Int)
  : AtLeastOnceProjection[Offset, EventEnvelope[HelloWorldEvent]] = {

    val tag = HelloWorldEvent.Tag.allTags.toSeq(index).tag

    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[HelloWorldEvent]] =
      EventSourcedProvider.eventsByTag[HelloWorldEvent](
        system = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        /*readJournalPluginId = "tenant.cassandra-query-journal-plugin.t1",*/
        tag = tag)

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("HelloWorldProjection", tag),
      sourceProvider,
      handler = () =>
        new HelloWorldProjectionHandler(tag, system)
    )
  }

}