package com.lagom.example

import com.lagom.example.api.App
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.dns.DnsServiceLocatorComponents
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Mode}

final class Loader extends ApplicationLoader {
  override def load(context: Context): Application = context.environment.mode match {
    case Mode.Dev => (new App(context) with LagomDevModeComponents).application
    case _        => (new App(context) with DnsServiceLocatorComponents).application
  }
}
