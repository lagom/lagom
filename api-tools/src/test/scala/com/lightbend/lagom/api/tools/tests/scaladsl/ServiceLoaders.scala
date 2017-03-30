/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.tools.tests.scaladsl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer }
import play.api.libs.ws.ahc.AhcWSComponents

class AclServiceLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new AclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }
  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new AclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def describeServices = List(readDescriptor[AclService])
}

abstract class AclServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with AhcWSComponents {
  override lazy val lagomServer = LagomServer.forService(bindService[AclService].to(new AclServiceImpl))
}

// ---------------------------------------

class NoAclServiceLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new NoAclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new NoAclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def describeServices = List(readDescriptor[NoAclService])
}

abstract class NoAclServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with AhcWSComponents {

  override lazy val lagomServer = LagomServer.forService(bindService[NoAclService].to(new NoAclServiceImpl))
}
// ---------------------------------------
class UndescribedServiceLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new NoAclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }
  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new NoAclServiceApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }
  override def describeServices = Nil
}

abstract class UndescribedServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with AhcWSComponents {
  override lazy val lagomServer = LagomServer.forService(bindService[UndescribedService].to(new UndescribedServiceImpl))
}
// ---------------------------------------
