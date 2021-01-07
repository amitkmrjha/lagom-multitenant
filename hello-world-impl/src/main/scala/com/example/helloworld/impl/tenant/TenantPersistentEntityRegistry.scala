package com.example.helloworld.impl.tenant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.query.Offset
import akka.stream.scaladsl
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.scaladsl.persistence.AbstractPersistentEntityRegistry
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, EventStreamElement, PersistentEntity, PersistentEntityRef, PersistentEntityRegistry}

import scala.reflect.ClassTag

 class TenantPersistentEntityRegistry(system: ActorSystem, tenantPlugins:Seq[TenantPersistencePlugin] = Seq.empty) {

  private val log = Logging.getLogger(system, getClass)

   private lazy val delegateRegistryMap : Map[TenantPersistenceId,PersistentEntityRegistry] = {
     tenantPlugins.map(tenantPlugin => tenantPlugin.tenantPersistenceId ->  new AbstractPersistentEntityRegistry(system){
       private val log = Logging.getLogger(system, getClass)
       //CassandraKeyspaceConfig.validateKeyspace("cassandra-journal", system.settings.config, log)
       //CassandraKeyspaceConfig.validateKeyspace("cassandra-snapshot-store", system.settings.config, log)
       protected override val name: Option[String] = Option(s"CassandraPersistentEntityRegistry-${tenantPlugin.tenantPersistenceId.tenantId}")
       println()
       println(s"journalPluginId ${tenantPlugin.journalPlugin.pluginId}")
       println(s"snapshotPluginId ${tenantPlugin.snapshotPlugin.pluginId}")
       println(s"queryPluginId ${tenantPlugin.queryJournalPlugin.pluginId}")
       println(s"projectionPluginId ${tenantPlugin.projectionPlugin.pluginId}")
       println()
       println()

       protected override val journalPluginId = s"${tenantPlugin.journalPlugin.pluginId}"
       protected override val snapshotPluginId = s"${tenantPlugin.snapshotPlugin.pluginId}"
       protected override val queryPluginId = Some(s"${tenantPlugin.queryJournalPlugin.pluginId}")
     })
   }.toMap


   private def delegate(implicit tenantId:TenantPersistenceId):PersistentEntityRegistry = {
     delegateRegistryMap.get(tenantId) match {
       case Some(s) => s
       case None => throw new Exception(s"No PersistentEntityRegistry found for tenant id ${tenantId.tenantId}")
     }
   }

   def register(entityFactory: => PersistentEntity): Unit = {
     delegateRegistryMap.values.toSeq.foldLeft(Seq.empty[Unit]) {
       case (acc, pe) =>
         acc :+ pe.register(entityFactory)
     }
   }

   def refFor[P <: PersistentEntity: ClassTag](entityId: String)(implicit tenantId:TenantPersistenceId): PersistentEntityRef[P#Command] = {
     delegate.refFor(entityId)
   }
 }