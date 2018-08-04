package com.lagom.example

import com.lagom.example.api.App
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Mode}

final class Loader extends ApplicationLoader {
  override def load(context: Context): Application = (new App(context) with LagomDevModeComponents).application
}
