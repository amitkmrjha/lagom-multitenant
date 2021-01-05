package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._

case class TenantPersistencePlugin(tenantPersistenceId:TenantPersistenceId,
                                   projectionPlugin:TenantProjectionPlugin)
object TenantPersistencePlugin {
  def toTenantPersistencePlugin(system: ActorSystem): Seq[TenantPersistencePlugin] = {
    val  projectionPlugin = TenantProjectionPlugin.toTenantProjectionPlugin(system)
    projectionPlugin.map{e =>
      TenantPersistencePlugin(
        e.tenantPersistenceId,
        projectionPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantProjectionPlugin.toDefault(e.tenantPersistenceId,system))
      )
    }
  }

}

case class TenantProjectionPlugin(tenantPersistenceId:TenantPersistenceId,config:Config)
object TenantProjectionPlugin{
  def toTenantProjectionPlugin(system: ActorSystem): Seq[TenantProjectionPlugin] = {
    system.settings.config.getConfig("tenant.akka-projection-cassandra-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        TenantProjectionPlugin(TenantPersistenceId(k) , v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            "akka.projection.cassandra"
          )
        ))
    }.toSeq
  }
  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantProjectionPlugin  = {
    TenantProjectionPlugin(tenantPersistenceId ,
      system.settings.config.getConfig(
        "akka.projection.cassandra"
      ))
  }
}
case class TenantPersistenceId(tenantId:String)
case class TenantBoundStatement(tenantId:TenantPersistenceId, boundStatement:BoundStatement)

