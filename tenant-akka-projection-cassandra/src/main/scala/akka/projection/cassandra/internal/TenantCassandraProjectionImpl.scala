package akka.projection.cassandra.internal
import akka.Done
import akka.actor.typed.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.projection.RunningProjection.AbortProjectionException
import akka.projection.{ProjectionId, ProjectionOffsetManagement, RunningProjection, StatusObserver}
import akka.projection.internal.{HandlerStrategy, InternalProjectionState, OffsetStrategy, ProjectionSettings, RestartBackoffSettings}
import akka.projection.scaladsl.SourceProvider
import akka.stream.scaladsl.Source

import scala.concurrent.{ExecutionContext, Future}

private[projection] class TenantCassandraProjectionImpl[Offset, Envelope](
                                                                           override val projectionId: ProjectionId,
                                                                           sourceProvider: SourceProvider[Offset, Envelope],
                                                                           settingsOpt: Option[ProjectionSettings],
                                                                           restartBackoffOpt: Option[RestartBackoffSettings],
                                                                           override val  offsetStrategy: OffsetStrategy,
                                                                           handlerStrategy: HandlerStrategy,
                                                                           override val statusObserver: StatusObserver[Envelope],
                                                                           offsetStorePluginId: Option[String] = None
     ) extends CassandraProjectionImpl[Offset, Envelope](
      projectionId,
      sourceProvider,
      settingsOpt,
      restartBackoffOpt,
      offsetStrategy,
      handlerStrategy,statusObserver) {

  private def copy(
                    settingsOpt: Option[ProjectionSettings] = this.settingsOpt,
                    restartBackoffOpt: Option[RestartBackoffSettings] = this.restartBackoffOpt,
                    offsetStrategy: OffsetStrategy = this.offsetStrategy,
                    handlerStrategy: HandlerStrategy = this.handlerStrategy,
                    statusObserver: StatusObserver[Envelope] = this.statusObserver,
                    offsetStorePluginId: Option[String] = this.offsetStorePluginId): CassandraProjectionImpl[Offset, Envelope] =
    new TenantCassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt,
      restartBackoffOpt,
      offsetStrategy,
      handlerStrategy,
      statusObserver,
      offsetStorePluginId)
  /*
   * Build the final ProjectionSettings to use, if currently set to None fallback to values in config file
   */
  private def settingsOrDefaults(implicit system: ActorSystem[_]): ProjectionSettings = {
    val settings = settingsOpt.getOrElse(ProjectionSettings(system))
    restartBackoffOpt match {
      case None    => settings
      case Some(r) => settings.copy(restartBackoff = r)
    }
  }

  override def run()(implicit system: ActorSystem[_]): RunningProjection = {
    new CassandraInternalProjectionState(settingsOrDefaults,offsetStorePluginId).newRunningInstance()
  }

  private class CassandraInternalProjectionState(val settings: ProjectionSettings,offsetStorePluginId: Option[String])(implicit val system: ActorSystem[_])
    extends InternalProjectionState[Offset, Envelope](
      projectionId,
      sourceProvider,
      offsetStrategy,
      handlerStrategy,
      statusObserver,
      settings) {

    override implicit def executionContext: ExecutionContext = system.executionContext
    override def logger: LoggingAdapter = Logging(system.classicSystem, this.getClass)

    //private val offsetStore = new CassandraOffsetStore(system)
    private val offsetStore = new TenantCassandraOffsetStore(system,offsetStorePluginId)

    override def readOffsets(): Future[Option[Offset]] =
      offsetStore.readOffset(projectionId)

    override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] =
      offsetStore.saveOffset(projectionId, offset)

    private[projection] def newRunningInstance(): RunningProjection = {
      new CassandraRunningProjection(RunningProjection.withBackoff(() => mappedSource(), settings), offsetStore, this)
    }

  }

  private class CassandraRunningProjection(
                                            source: Source[Done, _],
                                            offsetStore: TenantCassandraOffsetStore,
                                            projectionState: CassandraInternalProjectionState)(implicit system: ActorSystem[_])
    extends RunningProjection
      with ProjectionOffsetManagement[Offset] {

    private val streamDone = source.run()

    override def stop(): Future[Done] = {
      projectionState.killSwitch.shutdown()
      // if the handler is retrying it will be aborted by this,
      // otherwise the stream would not be completed by the killSwitch until after all retries
      projectionState.abort.failure(AbortProjectionException)
      streamDone
    }
    // ProjectionOffsetManagement
    override def getOffset(): Future[Option[Offset]] = {
      offsetStore.readOffset(projectionId)
    }
    // ProjectionOffsetManagement
    override def setOffset(offset: Option[Offset]): Future[Done] = {
      offset match {
        case Some(o) =>
          offsetStore.saveOffset(projectionId, o)
        case None =>
          offsetStore.clearOffset(projectionId)
      }
    }
  }

  def withProjectionPlugin(projectionPlugin:String): CassandraProjectionImpl[Offset, Envelope] = {
    copy(offsetStorePluginId = Option(projectionPlugin))
  }

}
