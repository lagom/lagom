/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface UsersService extends Service {

  ServiceCall<String, String> login();

  @Override
  // #with-auto-acl
  default Descriptor descriptor() {
    return named("users")
        .withCalls(restCall(Method.POST, "/api/users/login", this::login))
        .withAutoAcl(true);
    // #with-auto-acl
  }
}
