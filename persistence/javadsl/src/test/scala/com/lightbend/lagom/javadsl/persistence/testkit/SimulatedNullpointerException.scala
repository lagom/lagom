/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.testkit

import scala.util.control.NoStackTrace

/**
 * to avoid fat stack trace logging in tests
 */
class SimulatedNullpointerException extends NullPointerException("simulated NPE") with NoStackTrace
