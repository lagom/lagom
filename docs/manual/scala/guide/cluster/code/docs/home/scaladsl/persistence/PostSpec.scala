/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#unit-test
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.Done
import akka.actor.ActorSystem
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.InvalidCommandException
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver
import com.typesafe.config.ConfigFactory
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

class PostSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TypeCheckedTripleEquals {

  val system = ActorSystem("PostSpec", JsonSerializerRegistry.actorSystemSetupFor(BlogPostSerializerRegistry))

  override def afterAll(): Unit = {
    Await.ready(system.terminate, 10.seconds)
  }

  "Blog Post entity" must {
    "handle AddPost" in {
      val driver  = new PersistentEntityTestDriver(system, new Post, "post-1")
      val content = PostContent("Title", "Body")
      val outcome = driver.run(AddPost(content))
      outcome.events should ===(List(PostAdded("post-1", content)))
      outcome.state.published should ===(false)
      outcome.state.content should ===(Some(content))
      outcome.replies should ===(List(AddPostDone("post-1")))
      outcome.issues should be(Nil)
    }

    "validate title" in {
      val driver  = new PersistentEntityTestDriver(system, new Post, "post-1")
      val outcome = driver.run(AddPost(PostContent("", "Body")))
      outcome.replies.head.getClass should be(classOf[InvalidCommandException])
      outcome.events.size should ===(0)
      outcome.issues should be(Nil)
    }

    "handle ChangeBody" in {
      val driver = new PersistentEntityTestDriver(system, new Post, "post-1")
      driver.run(AddPost(PostContent("Title", "Body")))

      val outcome = driver.run(ChangeBody("New body 1"), ChangeBody("New body 2"))
      outcome.events should ===(List(BodyChanged("post-1", "New body 1"), BodyChanged("post-1", "New body 2")))
      outcome.state.published should ===(false)
      outcome.state.content.get.body should ===("New body 2")
      outcome.replies should ===(List(Done, Done))
      outcome.issues should be(Nil)
    }

  }
}
//#unit-test
