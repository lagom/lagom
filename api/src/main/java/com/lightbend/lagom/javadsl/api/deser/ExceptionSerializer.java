/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import com.lightbend.lagom.internal.api.JacksonPlaceholderExceptionSerializer$;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;

import java.util.Collection;

public interface ExceptionSerializer {

    RawExceptionMessage serialize(Throwable exception, Collection<MessageProtocol> accept);

    Throwable deserialize(RawExceptionMessage message);

    /**
     * The default Jackson exception serializer.
     *
     * This is only a placeholder, the framework will provide the actual implementation at runtime.
     */
    ExceptionSerializer JACKSON = JacksonPlaceholderExceptionSerializer$.MODULE$;

    /**
     * The default serializer factory.
     */
    ExceptionSerializer DEFAULT = JACKSON;

}
