#!/usr/bin/env bash

# Copyright (C) Lightbend Inc. <https://www.lightbend.com>

# shellcheck source=bin/scriptLib
. "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/scriptLib"

printMessage "VALIDATE FRAMEWORK CODE"
sbt +headerCheckAll \
  scalafmtAll scalafmtSbt \
  javafmtCheckAll

printMessage "VALIDATE DOCS CODE"
pushd docs
sbt headerCheckAll \
  scalafmtAll scalafmtSbt \
  javafmtCheckAll
popd

printMessage "ALL VALIDATIONS DONE"
