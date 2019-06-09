#!/usr/bin/env bash

# Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>

. "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/scriptLib"

printMessage "VALIDATE FRAMEWORK CODE"
sbt +headerCheck +test:headerCheck multi-jvm:headerCheck \
  scalafmtAll scalafmtSbt \
  javafmt test:javafmt multi-jvm:javafmt

printMessage "VALIDATE DOCS CODE"
pushd docs
sbt headerCheck test:headerCheck \
  scalafmtAll scalafmtSbt \
  javafmt test:javafmt
popd

git diff --exit-code || (
  echo "WARN: Code changed after format and license headers validation. See diff above."
  echo "You need to commit the new changes or amend the existing commit."
  false
)

printMessage "ALL VALIDATIONS DONE"
