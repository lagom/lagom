/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.testkit

import scala.util.control.NoStackTrace

/**
 * to avoid fat stack trace logging in tests
 */
class SimulatedNullpointerException extends NullPointerException("simulated NPE") with NoStackTrace
