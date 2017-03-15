package com.lightbend.lagom.api.tools.tests.scaladsl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer}
import play.api.libs.ws.ahc.AhcWSComponents

class StubServiceLoader extends LagomApplicationLoader {

	override def load(context: LagomApplicationContext): LagomApplication =
		new StubServiceApplication(context) {
			override def serviceLocator: ServiceLocator = NoServiceLocator
		}

	override def loadDevMode(context: LagomApplicationContext): LagomApplication =
		new StubServiceApplication(context) {
			override def serviceLocator: ServiceLocator = NoServiceLocator
		}

  override def describeServices = List(
    readDescriptor[AclService],
    readDescriptor[NoAclService]
  )
}

abstract class StubServiceApplication(context: LagomApplicationContext)
	extends LagomApplication(context)
		with AhcWSComponents {

	override lazy val lagomServer = LagomServer.forServices(
		bindService[AclService].to(new AclServiceImpl),
		bindService[NoAclService].to(new NoAclServiceImpl)
	)
}