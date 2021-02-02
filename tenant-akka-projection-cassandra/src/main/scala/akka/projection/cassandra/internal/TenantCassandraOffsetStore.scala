package akka.projection.cassandra.internal

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.{MergeableOffset, ProjectionId}
import akka.projection.internal.OffsetSerialization
import akka.projection.internal.OffsetSerialization.SingleOffset
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import com.datastax.oss.driver.api.core.cql.{Row, Statement}

import java.time.{Clock, Instant}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

private[projection] class TenantCassandraOffsetStore(system: ActorSystem[_],clock: Clock, offsetStorePluginId: Option[String]) {
  private val offsetSerialization = new OffsetSerialization(system)
  import offsetSerialization.fromStorageRepresentation
  import offsetSerialization.toStorageRepresentation

  private implicit val executionContext: ExecutionContext = system.executionContext
  private val cassandraSettings = CassandraSettings(
    system.settings.config.getConfig(offsetStorePluginId.getOrElse( "akka.projection.cassandra"))
  )

  private val tenantSession: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(cassandraSettings.sessionConfigPath)
  val keyspace: String = cassandraSettings.keyspace
  val table: String = cassandraSettings.table
  private val cassandraPartitions = 5

  def this(system: ActorSystem[_],offsetStorePluginId: Option[String]) =
    this(system, Clock.systemUTC(),offsetStorePluginId)

  private def selectOne[T <: Statement[T]](stmt: Statement[T]): Future[Option[Row]] = {
    tenantSession.selectOne(stmt.setExecutionProfileName(cassandraSettings.profile))
  }

  private def execute[T <: Statement[T]](stmt: Statement[T]): Future[Done] = {
    tenantSession.executeWrite(stmt.setExecutionProfileName(cassandraSettings.profile))
  }

  def readOffset[Offset](projectionId: ProjectionId): Future[Option[Offset]] = {
    val partition = idToPartition(projectionId)
    tenantSession
      .prepare(
        s"SELECT projection_key, offset, manifest FROM $keyspace.$table WHERE projection_name = ? AND partition = ? AND projection_key = ?")
      .map(_.bind(projectionId.name, partition, projectionId.key))
      .flatMap(selectOne)
      .map { maybeRow =>
        maybeRow.map(row => fromStorageRepresentation[Offset](row.getString("offset"), row.getString("manifest")))
      }
  }

  def saveOffset[Offset](projectionId: ProjectionId, offset: Offset): Future[Done] = {
    // a partition is calculated to ensure some distribution of projection rows across cassandra nodes, but at the
    // same time let us query all rows for a single projection_name easily
    val partition = idToPartition(projectionId)
    offset match {
      case _: MergeableOffset[_] =>
        throw new IllegalArgumentException("The CassandraOffsetStore does not currently support MergeableOffset")
      case _ =>
        val SingleOffset(_, manifest, offsetStr, _) =
          toStorageRepresentation(projectionId, offset).asInstanceOf[SingleOffset]
        tenantSession
          .prepare(
            s"INSERT INTO $keyspace.$table (projection_name, partition, projection_key, offset, manifest, last_updated) VALUES (?, ?, ?, ?, ?, ?)")
          .map(_.bind(projectionId.name, partition, projectionId.key, offsetStr, manifest, Instant.now(clock)))
          .flatMap(execute)
    }
  }

  def clearOffset(projectionId: ProjectionId): Future[Done] = {
    tenantSession
      .prepare(s"DELETE FROM $keyspace.$table WHERE projection_name = ? AND partition = ? AND projection_key = ?")
      .map(_.bind(projectionId.name, idToPartition(projectionId), projectionId.key))
      .flatMap(execute)
  }

  def createKeyspaceAndTable(): Future[Done] = {
    tenantSession
      .executeDDL(
        s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor':1 }")
      .flatMap(_ => tenantSession.executeDDL(s"""
                                          |CREATE TABLE IF NOT EXISTS $keyspace.$table (
                                          |  projection_name text,
                                          |  partition int,
                                          |  projection_key text,
                                          |  offset text,
                                          |  manifest text,
                                          |  last_updated timestamp,
                                          |  PRIMARY KEY ((projection_name, partition), projection_key))
        """.stripMargin.trim))
  }

  private[cassandra] def idToPartition[Offset](projectionId: ProjectionId): Integer = {
    Integer.valueOf(Math.abs(projectionId.key.hashCode() % cassandraPartitions))
  }

}