package com.example.helloworld.impl.tenant

import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraPersistenceComponents, WriteSideCassandraPersistenceComponents}
import akka.actor.{ActorSystem => ActorSystemClassic}

trait TenantPersistenceComponent extends /*CassandraPersistenceComponents*/ WriteSideCassandraPersistenceComponents {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins: Seq[TenantPersistencePlugin] = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val  tenantPersistentEntityRegistry: TenantPersistentEntityRegistry = new TenantPersistentEntityRegistry(actorSystem,tenantPlugins)
}
