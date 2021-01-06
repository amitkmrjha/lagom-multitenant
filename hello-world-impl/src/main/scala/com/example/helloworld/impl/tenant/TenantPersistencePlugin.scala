package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._

case class TenantPersistencePlugin(tenantPersistenceId:TenantPersistenceId,
                                   sessionPlugin:TenantSessionPlugin)
object TenantPersistencePlugin {
  def toTenantPersistencePlugin(system: ActorSystem): Seq[TenantPersistencePlugin] = {
    val  projectionPlugin = TenantSessionPlugin.toTenantProjectionPlugin(system)
    projectionPlugin.map{e =>
      TenantPersistencePlugin(
        e.tenantPersistenceId,
        projectionPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantSessionPlugin.toDefault(e.tenantPersistenceId,system))
      )
    }
  }

}

case class TenantSessionPlugin(tenantPersistenceId:TenantPersistenceId, keyspace:String,config:Config)
object TenantSessionPlugin{
  def toTenantProjectionPlugin(system: ActorSystem): Seq[TenantSessionPlugin] = {
    system.settings.config.getConfig("tenant.alpakka-cassandra-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        val config = v.asInstanceOf[ConfigObject].toConfig
        val keyspace: String = config.getString("read-side-keyspace")
        TenantSessionPlugin(TenantPersistenceId(k) ,keyspace, config.withFallback(
          system.settings.config.getConfig(
            "alpakka.cassandra"
          )
        ))
    }.toSeq
  }
  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantSessionPlugin  = {
    TenantSessionPlugin(tenantPersistenceId ,s"default-${tenantPersistenceId.tenantId}",
      system.settings.config.getConfig(
        "alpakka.cassandra"
      ))
  }
}
case class TenantPersistenceId(tenantId:String)
case class TenantBoundStatement(tenantId:TenantPersistenceId, boundStatement:BoundStatement)

