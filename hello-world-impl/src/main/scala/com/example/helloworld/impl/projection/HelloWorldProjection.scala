package com.example.helloworld.impl.projection
/*
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
import com.example.helloworld.impl.{PortfolioEvent, PortfolioState}*/

object HelloWorldProjection {
  /*def init(
            system: ActorSystem[_]
            /*repository: ItemPopularityRepository*/): Unit = {
    ShardedDaemonProcess(system).init(
      name = "ItemPopularityProjection",
      /*PortfolioEvent.Tag.size,*/1,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }


  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   /*repository: ItemPopularityRepository,*/
                                   index: Int)
  : AtLeastOnceProjection[Offset, EventEnvelope[PortfolioEvent]] = {
    /*val tag = ShoppingCart.tags(index)*/

    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[PortfolioEvent]] =
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](
        system = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        tag = tag)

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("ItemPopularityProjection", tag),
      sourceProvider,
      handler = () =>
        new ItemPopularityProjectionHandler(tag, system, repository)
    )
  }*/

}
