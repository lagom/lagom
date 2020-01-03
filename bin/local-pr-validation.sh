#!/usr/bin/env bash

# Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>

# shellcheck source=bin/scriptLib
. "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/scriptLib"

printMessage "VALIDATE FRAMEWORK CODE"
sbt +headerCheck +test:headerCheck multi-jvm:headerCheck \
  scalafmtAll scalafmtSbt \
  javafmtCheckAll

printMessage "VALIDATE DOCS CODE"
pushd docs
sbt headerCheck test:headerCheck \
  scalafmtAll scalafmtSbt \
  javafmtCheckAll
popd

printMessage "ALL VALIDATIONS DONE"
