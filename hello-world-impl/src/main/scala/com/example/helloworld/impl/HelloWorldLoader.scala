package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.helloworld.api.HelloWorldService
import com.example.helloworld.impl.daos.stock.StockDao
import com.example.helloworld.impl.entity.{PortfolioEntity, StockEntity}
import com.example.helloworld.impl.tenant.TenantPersistenceComponent
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.softwaremill.macwire._
import akka.actor.typed.scaladsl.adapter._
import com.example.helloworld.impl.daos.portfolio.PortfolioDao
import com.example.helloworld.impl.projection.publish.HelloWorldPublishProjection
import com.example.helloworld.impl.projection.query.HelloWorldQueryProjection
import com.example.helloworld.impl.projection.subscribe.HelloWorldEventConsumer


class HelloWorldLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new HelloWorldApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HelloWorldApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HelloWorldService])
}

abstract class HelloWorldApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    /*with CassandraPersistenceComponents*/
    /*with LagomKafkaComponents*/
    with TenantPersistenceComponent
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[HelloWorldService](wire[HelloWorldServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = HelloWorldSerializerRegistry

  lazy val stockDao: StockDao = wire[StockDao]
  lazy val portfolioDao: PortfolioDao = wire[PortfolioDao]

  HelloWorldQueryProjection.init(actorSystem.toTyped,stockDao,portfolioDao,tenantPlugins )
  HelloWorldPublishProjection.init(actorSystem.toTyped,tenantPlugins)
  HelloWorldEventConsumer.init(actorSystem.toTyped)

  tenantPersistentEntityRegistry.register(wire[PortfolioEntity])
  tenantPersistentEntityRegistry.register(wire[StockEntity])
}
