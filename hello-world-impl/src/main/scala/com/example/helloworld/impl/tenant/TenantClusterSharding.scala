package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import com.example.helloworld.impl.{PortfolioBehavior, PortfolioState, StockBehavior, StockState}

class TenantClusterSharding(system:ActorSystem,clusterSharding: ClusterSharding,tenantPlugins: Seq[TenantPersistencePlugin]) {
  import TenantClusterSharding._

  /*def init[M, E](entity:Seq[Entity[M, E]])= {
    entity.map{t =>
      clusterSharding.init(t)
    }
  }*/

  def initPortfolio(tenants:Seq[TenantPersistencePlugin]):Unit= {
    tenants.map{t =>
      val portfolioTypeKey = toTenantTypeKey(PortfolioState.typeKey)(t.tenantPersistenceId)
      clusterSharding.init(
          Entity(portfolioTypeKey)(
          entityContext => PortfolioBehavior.create(entityContext,t)
        )
      )
    }
  }
  def initStock(tenants:Seq[TenantPersistencePlugin]):Unit= {
    tenants.map{t =>
      val portfolioTypeKey = toTenantTypeKey(StockState.typeKey)(t.tenantPersistenceId)
      clusterSharding.init(
        Entity(portfolioTypeKey)(
          entityContext => StockBehavior.create(entityContext,t)
        )
      )
    }
  }

  def entityRefFor[M](typeKey: EntityTypeKey[M], entityId: String)(implicit tenantPersistenceId: TenantPersistenceId): EntityRef[M] = {
    clusterSharding.entityRefFor(toTenantTypeKey(typeKey), entityId)
  }
}

object TenantClusterSharding{

  def toTenantTypeKey[M](typeKey:EntityTypeKey[M])(implicit tenantPersistenceId: TenantPersistenceId):EntityTypeKey[M] = {
    EntityTypeKey(s"${tenantPersistenceId.tenantId}${typeKey.name}")
  }
}


