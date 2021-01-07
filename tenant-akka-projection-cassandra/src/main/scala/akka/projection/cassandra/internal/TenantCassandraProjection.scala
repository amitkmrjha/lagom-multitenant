package akka.projection.cassandra.internal

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.ProjectionId
import akka.projection.internal.{AtLeastOnce, NoopStatusObserver, SingleHandlerStrategy}
import akka.projection.scaladsl.{AtLeastOnceProjection, Handler, SourceProvider}

import scala.concurrent.Future

object TenantCassandraProjection {

  def atLeastOnce[Offset, Envelope](
                                     projectionId: ProjectionId,
                                     sourceProvider: SourceProvider[Offset, Envelope],
                                     handler: () => Handler[Envelope]
                                     ): AtLeastOnceProjection[Offset, Envelope] =atLeastOnce(projectionId,sourceProvider,handler)

  def atLeastOnce[Offset, Envelope](
                                     projectionId: ProjectionId,
                                     sourceProvider: SourceProvider[Offset, Envelope],
                                     handler: () => Handler[Envelope],
                                     offsetStorePluginId: String ): AtLeastOnceProjection[Offset, Envelope] =
    new TenantCassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = SingleHandlerStrategy(handler),
      statusObserver = NoopStatusObserver).withProjectionPlugin(offsetStorePluginId)

  def createOffsetTableIfNotExists(offsetStorePluginId:Option[String]= None)(implicit system: ActorSystem[_]): Future[Done] = {
    val offsetStore = new TenantCassandraOffsetStore(system,offsetStorePluginId)
    offsetStore.createKeyspaceAndTable()
  }
}
