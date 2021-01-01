package com.example.helloworld.impl.utils
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import scala.concurrent.{ExecutionContext, Future}

object FutureConverter {

    implicit class FutureOps[T](future: Future[T])
                               (implicit ec: ExecutionContext) {

      def toOption: Future[Option[T]] = {
        future.map(t => Option(t))
          .recover { case _: Exception => None }
      }
    }

    implicit class FutureOptionOps[T](futureOption: Future[Option[T]])
                                     (implicit ec: ExecutionContext) {

      def toFutureT(errorMsg: String): Future[T] = {
        futureOption.map {
          case Some(entity) => entity
          case None => throw NotFound(errorMsg)
        }
      }
    }
}
