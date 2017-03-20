package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents

class JdbcBlogApplicationLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BlogApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BlogApplication(context) with LagomDevModeComponents

  override def describeServices = List(
    readDescriptor[BlogService]
  )
}

//#load-components
abstract class BlogApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with JdbcPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {
//#load-components

  // Bind the services that this server provides
  override lazy val lagomServer = LagomServer.forServices(
// TODO for docs BlogServiceImpl has to be class, no trait
// bindService[BlogService].to(wire[BlogServiceImpl])
  )

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = BlogPostSerializerRegistry

  // Register the Blog application persistent entity
  persistentEntityRegistry.register(wire[Post])
}

