/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cluster



class ProjectorRegistrySpecMultiJvmNode1 extends ProjectorRegistrySpec
class ProjectorRegistrySpecMultiJvmNode2 extends ProjectorRegistrySpec
class ProjectorRegistrySpecMultiJvmNode3 extends ProjectorRegistrySpec

class ProjectorRegistrySpec  extends ClusteredMultiNodeUtils {

  "A ProjectorRegistry" must {
    "register a projector" in {
    }
  }
}
