package com.example.helloworld.impl.tenant

import akka.Done
import akka.actor.{ActorSystem => ActorSystemClassic}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

trait TenantProjectionComponent {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  val session: TenantCassandraSession = new TenantCassandraSession(actorSystem,tenantPlugins)
  Await.ready(session.executeCreateKeySpace,100.seconds)
}
