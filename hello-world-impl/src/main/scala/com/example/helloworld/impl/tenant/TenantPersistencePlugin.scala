package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._

case class TenantPersistencePlugin(tenantPersistenceId:TenantPersistenceId,
                                   journalPlugin: TenantJournalPlugin,
                                   snapshotPlugin:TenantSnapShotPlugin,
                                   queryJournalPlugin: TenantQueryJournalPlugin,
                                   projectionPlugin:TenantProjectionPlugin)
object TenantPersistencePlugin {
  def toTenantPersistencePlugin(system: ActorSystem): Seq[TenantPersistencePlugin] = {
    val journalPlugin = TenantJournalPlugin.toTenantJournalPlugin(system)
    val snapshotPlugin = TenantSnapShotPlugin.toTenantSnapShotPlugin(system)
    val queryJournalPlugin = TenantQueryJournalPlugin.toTenantQueryJournalPlugin(system)
    val  projectionPlugin = TenantProjectionPlugin.toTenantProjectionPlugin(system)
    journalPlugin.map{e =>
      TenantPersistencePlugin(
        e.tenantPersistenceId,
        e,
        snapshotPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantSnapShotPlugin.toDefault(e.tenantPersistenceId,system)),
        queryJournalPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantQueryJournalPlugin.toDefault(e.tenantPersistenceId,system)),
        projectionPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantProjectionPlugin.toDefault(e.tenantPersistenceId,system))
      )
    }
  }

}

case class TenantJournalPlugin(tenantPersistenceId:TenantPersistenceId,config:Config)

object TenantJournalPlugin{
  def toTenantJournalPlugin(system: ActorSystem): Seq[TenantJournalPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-journal-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        TenantJournalPlugin(TenantPersistenceId(k) , v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            "cassandra-journal"
          )
        ))
    }.toSeq
  }

}
case class TenantSnapShotPlugin(tenantPersistenceId:TenantPersistenceId,config:Config)
object TenantSnapShotPlugin{
  def toTenantSnapShotPlugin(system: ActorSystem): Seq[TenantSnapShotPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-snapshot-store-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        TenantSnapShotPlugin(TenantPersistenceId(k) , v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            "cassandra-snapshot-store"
          )
        ))
    }.toSeq
  }


  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantSnapShotPlugin = {
    TenantSnapShotPlugin(tenantPersistenceId ,
      system.settings.config.getConfig(
        "cassandra-snapshot-store"
      ))
  }
}

case class TenantQueryJournalPlugin(tenantPersistenceId:TenantPersistenceId,config:Config)
object TenantQueryJournalPlugin{
  def toTenantQueryJournalPlugin(system: ActorSystem): Seq[TenantQueryJournalPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-query-journal-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        TenantQueryJournalPlugin(TenantPersistenceId(k) , v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            "cassandra-query-journal"
          )
        ))
    }.toSeq
  }
  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantQueryJournalPlugin  = {
    TenantQueryJournalPlugin(tenantPersistenceId ,
      system.settings.config.getConfig(
        "cassandra-query-journal"
      ))
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

