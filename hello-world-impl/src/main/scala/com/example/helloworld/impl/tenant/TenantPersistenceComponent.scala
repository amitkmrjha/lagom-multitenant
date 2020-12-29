package com.example.helloworld.impl.tenant

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import akka.actor.{ ActorSystem => ActorSystemClassic }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

trait TenantPersistenceComponent extends CassandraPersistenceComponents{
  def actorSystem: ActorSystemClassic
  def clusterSharding:ClusterSharding
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val tenantClusterSharding: TenantClusterSharding = new TenantClusterSharding(actorSystem,clusterSharding,tenantPlugins)
}
