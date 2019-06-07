/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.HeaderFilter;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface EchoService extends Service {

  ServiceCall<String, String> echo();

  @Override
  // #header-filter-composition
  default Descriptor descriptor() {
    return named("echo")
        .withCalls(namedCall("echo", this::echo))
        .withHeaderFilter(HeaderFilter.composite(new FooFilter(), new BarFilter()))
        .withAutoAcl(true);
    // #header-filter-composition
  }
}

abstract class ChattyFilter implements HeaderFilter {

  private final String name;
  private Logger log = LoggerFactory.getLogger(ChattyFilter.class);

  ChattyFilter(String name) {
    this.name = name;
  }

  @Override
  public RequestHeader transformClientRequest(RequestHeader request) {
    log.debug(name + " - transforming Client Request");
    return request;
  }

  @Override
  public RequestHeader transformServerRequest(RequestHeader request) {
    log.debug(name + " - transforming Server Request");
    return request;
  }

  @Override
  public ResponseHeader transformServerResponse(ResponseHeader response, RequestHeader request) {
    log.debug(name + " - transforming Server Response");
    return response;
  }

  @Override
  public ResponseHeader transformClientResponse(ResponseHeader response, RequestHeader request) {
    log.debug(name + " - transforming Client Response");
    return response;
  }
}

class BarFilter extends ChattyFilter {
  public BarFilter() {
    super("Bar");
  }
}

class FooFilter extends ChattyFilter {
  public FooFilter() {
    super("Foo");
  }
}
