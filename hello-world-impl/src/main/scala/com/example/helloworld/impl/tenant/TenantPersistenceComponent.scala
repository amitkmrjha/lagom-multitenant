package com.example.helloworld.impl.tenant

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import akka.actor.{ActorSystem => ActorSystemClassic}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.internal.scaladsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

trait TenantPersistenceComponent extends CassandraPersistenceComponents {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val  tenantPersistentEntityRegistry: TenantPersistentEntityRegistry = new TenantPersistentEntityRegistry(actorSystem,tenantPlugins)
}
