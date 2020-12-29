package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import com.example.helloworld.impl.PortfolioCommand

class TenantClusterSharding(system:ActorSystem,clusterSharding: ClusterSharding,tenantPlugins: Seq[TenantPersistencePlugin]) {
  import TenantClusterSharding._
  /*def this(system:ActorSystem,clusterSharding: ClusterSharding) = {
    this(
      system,
      clusterSharding,
      tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(system)
    )
  }*/

  def init[M, E](entity:Seq[Entity[M, E]]): Unit= {
    entity.map{t =>
      clusterSharding.init(
        t
      )
    }
  }


  def entityRefFor[M](typeKey: EntityTypeKey[M], entityId: String)(implicit tenantPersistenceId: TenantPersistenceId): EntityRef[M] = {
    clusterSharding.entityRefFor(tenantTypeKey(typeKey), entityId)
  }

}

object TenantClusterSharding{

  def tenantTypeKey[M](typeKey:EntityTypeKey[M])(implicit tenantPersistenceId: TenantPersistenceId):EntityTypeKey[M] = {
    EntityTypeKey(s"${tenantPersistenceId.tenantId}${typeKey.name}")
  }
}

case class TenantEntityBehaviour[M](tenantPersistenceId:TenantPersistenceId,typeKey: EntityTypeKey[M],createBehavior: Behavior[M])


