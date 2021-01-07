package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._

case class TenantPersistencePlugin(tenantPersistenceId:TenantPersistenceId,
                                   journalPlugin: TenantJournalPlugin,
                                   snapshotPlugin:TenantSnapShotPlugin,
                                   queryJournalPlugin: TenantQueryJournalPlugin,
                                   projectionPlugin: TenantProjectionCassandraPlugin)
object TenantPersistencePlugin {
  def toTenantPersistencePlugin(system: ActorSystem): Seq[TenantPersistencePlugin] = {
    val journalPlugin = TenantJournalPlugin.toTenantJournalPlugin(system)
    val snapshotPlugin = TenantSnapShotPlugin.toTenantSnapShotPlugin(system)
    val queryJournalPlugin = TenantQueryJournalPlugin.toTenantQueryJournalPlugin(system)
    val projectionCassandraPlugin = TenantProjectionCassandraPlugin.toTenantProjectionPlugin(system)
    journalPlugin.map{e =>
      TenantPersistencePlugin(
        e.tenantPersistenceId,
        e,
        snapshotPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantSnapShotPlugin.toDefault(e.tenantPersistenceId,system)),
        queryJournalPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantQueryJournalPlugin.toDefault(e.tenantPersistenceId,system)),
        projectionCassandraPlugin.find(_.tenantPersistenceId == e.tenantPersistenceId).getOrElse(TenantProjectionCassandraPlugin.toDefault(e.tenantPersistenceId,system))
      )
    }
  }
}

case class TenantJournalPlugin(tenantPersistenceId:TenantPersistenceId,pluginId:String,config:Config)

object TenantJournalPlugin{
  val defaultJournalPlugin = "cassandra-journal"

  def toTenantJournalPlugin(system: ActorSystem): Seq[TenantJournalPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-journal-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        val id: String = s"tenant.cassandra-journal-plugin.${k}"
        TenantJournalPlugin(TenantPersistenceId(k) ,id, v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            defaultJournalPlugin
          )
        ))
    }.toSeq
  }

}
case class TenantSnapShotPlugin(tenantPersistenceId:TenantPersistenceId,pluginId:String,config:Config)
object TenantSnapShotPlugin{
  val defaultSnapShotPlugin = "cassandra-snapshot-store"
  def toTenantSnapShotPlugin(system: ActorSystem): Seq[TenantSnapShotPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-snapshot-store-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        val id: String = s"tenant.cassandra-snapshot-store-plugin.${k}"
        TenantSnapShotPlugin(TenantPersistenceId(k) ,id, v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            defaultSnapShotPlugin
          )
        ))
    }.toSeq
  }


  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantSnapShotPlugin = {
    TenantSnapShotPlugin(tenantPersistenceId ,defaultSnapShotPlugin,
      system.settings.config.getConfig(
        defaultSnapShotPlugin
      ))
  }
}

case class TenantQueryJournalPlugin(tenantPersistenceId:TenantPersistenceId,pluginId:String,config:Config)
object TenantQueryJournalPlugin{
  val defaultQueryJournalPlugin = "cassandra-query-journal"
  def toTenantQueryJournalPlugin(system: ActorSystem): Seq[TenantQueryJournalPlugin] = {
    system.settings.config.getConfig("tenant.cassandra-query-journal-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        val id: String = s"tenant.cassandra-query-journal-plugin.${k}"
        TenantQueryJournalPlugin(TenantPersistenceId(k) ,id, v.asInstanceOf[ConfigObject].toConfig.withFallback(
          system.settings.config.getConfig(
            defaultQueryJournalPlugin
          )
        ))
    }.toSeq
  }
  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantQueryJournalPlugin  = {
    TenantQueryJournalPlugin(tenantPersistenceId ,defaultQueryJournalPlugin,
      system.settings.config.getConfig(
        defaultQueryJournalPlugin
      ))
  }
}

case class TenantProjectionCassandraPlugin(tenantPersistenceId:TenantPersistenceId,pluginId:String, keyspace:String, config:Config)
object TenantProjectionCassandraPlugin{
  val defaultProjectionCassandraPlugin = "akka.projection.cassandra"
  def toTenantProjectionPlugin(system: ActorSystem): Seq[TenantProjectionCassandraPlugin] = {
    system.settings.config.getConfig("tenant.akka-projection-cassandra-plugin").root().asScala.map{
      case (k:String,v:ConfigValue) =>
        val config = v.asInstanceOf[ConfigObject].toConfig
        val keyspace: String = config.getString("offset-store.keyspace")
        val id: String = s"tenant.akka-projection-cassandra-plugin.${k}"
        TenantProjectionCassandraPlugin(TenantPersistenceId(k), id,keyspace, config.withFallback(
          system.settings.config.getConfig(
            defaultProjectionCassandraPlugin
          )
        ))
    }.toSeq
  }
  def toDefault(tenantPersistenceId:TenantPersistenceId,system: ActorSystem) : TenantProjectionCassandraPlugin  = {
    TenantProjectionCassandraPlugin(tenantPersistenceId,defaultProjectionCassandraPlugin ,s"default-${tenantPersistenceId.tenantId}",
      system.settings.config.getConfig(
        defaultProjectionCassandraPlugin
      ))
  }
}


case class TenantPersistenceId(tenantId:String)
