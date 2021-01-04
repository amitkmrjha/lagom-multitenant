package com.example.helloworld.impl.tenant

import akka.actor.{ActorSystem => ActorSystemClassic}
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}

trait TenantProjectionComponent {
  def actorSystem: ActorSystemClassic
  def tenantPlugins: Seq[TenantPersistencePlugin]
  //val session1: CassandraSession = CassandraSessionRegistry(actorSystem).sessionFor("tenant.akka-projection-cassandra-plugin.t1.session-config")
  //val session2: CassandraSession = CassandraSessionRegistry(actorSystem).sessionFor("tenant.akka-projection-cassandra-plugin.t2.session-config")
}
