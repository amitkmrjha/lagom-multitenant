package com.example.helloworld.impl.projection.query

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.query.Offset
import akka.projection.cassandra.internal.TenantCassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.example.helloworld.impl.HelloWorldEvent
import com.example.helloworld.impl.daos.portfolio.PortfolioDao
import com.example.helloworld.impl.daos.stock.StockDao
import com.example.helloworld.impl.tenant.TenantPersistencePlugin

object HelloWorldQueryProjection {
  def init(
            system: ActorSystem[_],
            stockDao: StockDao,
            portfolioDao: PortfolioDao,
            tenantPlugins: Seq[TenantPersistencePlugin]): Unit = {

    tenantPlugins.map { p =>
      TenantCassandraProjection.createOffsetTableIfNotExists(Option(p.projectionPlugin.pluginId))(system)
      ShardedDaemonProcess(system).init(
        name = s"HelloWorldQueryProjection-${p.tenantPersistenceId.tenantId}",
        HelloWorldEvent.Tag.allTags.size,
        index =>
          ProjectionBehavior(createProjectionFor(system, index, stockDao, portfolioDao, p)),
        ShardedDaemonProcessSettings(system),
        Some(ProjectionBehavior.Stop))
    }

  }


  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   index: Int,
                                   stockDao: StockDao,
                                   portfolioDao: PortfolioDao,
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
      projectionId = ProjectionId("HelloWorldQueryProjection", tag),
      sourceProvider,
      handler = () =>
        new HelloWorldQueryHandler(tag, system, stockDao, portfolioDao),
      tenantPlugin.projectionPlugin.pluginId
    )
  }

}
