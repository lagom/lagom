/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#imports
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRef
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

//#imports

trait BlogServiceImpl {
  trait BlogService extends Service {
    def addPost(id: String): ServiceCall[AddPost, String]

    override def descriptor = ???
  }

  //#service-impl
  class BlogServiceImpl(persistentEntities: PersistentEntityRegistry)(implicit ec: ExecutionContext)
      extends BlogService {

    persistentEntities.register(new Post)

    override def addPost(id: String): ServiceCall[AddPost, String] =
      ServiceCall { request: AddPost =>
        val ref: PersistentEntityRef[BlogCommand] =
          persistentEntities.refFor[Post](id)
        val reply: Future[AddPostDone] = ref.ask(request)
        reply.map(ack => "OK")
      }
  }
  //#service-impl

  class BlogServiceImpl2(persistentEntities: PersistentEntityRegistry)(implicit ec: ExecutionContext)
      extends BlogService {

    persistentEntities.register(new Post)

    //#service-impl2
    override def addPost(id: String) = ServiceCall { request =>
      val ref = persistentEntities.refFor[Post](id)
      ref.ask(request).map(ack => "OK")
    }
    //#service-impl2
  }

}
