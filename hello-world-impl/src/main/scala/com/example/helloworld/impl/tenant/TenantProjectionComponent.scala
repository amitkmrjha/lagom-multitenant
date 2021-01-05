package com.example.helloworld.impl.tenant

import akka.actor.{ActorSystem => ActorSystemClassic}

trait TenantProjectionComponent {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val session: TenantCassandraSession = new TenantCassandraSession(actorSystem,tenantPlugins)

}
