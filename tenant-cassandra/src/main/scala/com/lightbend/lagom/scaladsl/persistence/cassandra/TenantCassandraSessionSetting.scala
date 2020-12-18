package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import com.datastax.driver.core.BoundStatement
import com.typesafe.config.{ConfigObject, ConfigValue}

import scala.collection.JavaConverters._
import scala.collection.mutable

object TenantCassandraSessionSetting {
  def toTenantSettings(system: ActorSystem): Seq[TenantCassandraSessionSetting] = {
    val tenantConfig = system.settings.config.getConfig(
      "tenant.persistence.read-side.cassandra"
    )
    tenantConfig.root().asScala.map{
      case (k:String,v:ConfigValue) => TenantCassandraSessionSetting(TenantDataBaseId(k) , CassandraSessionSettings(v.asInstanceOf[ConfigObject].toConfig.withFallback(
        system.settings.config.getConfig(
          "lagom.persistence.read-side.cassandra"
        )
      )))
    }.toSeq
  }
}

case class TenantCassandraSessionSetting(tenantDataBase:TenantDataBaseId, setting:CassandraSessionSettings)
case class TenantDataBaseId(tenantId:String)
case class TenantBoundStatement(tenantId:TenantDataBaseId, boundStatement:BoundStatement)
