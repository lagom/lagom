/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import com.lightbend.lagom.dev.PortAssigner.{ Port, PortRange }
import org.scalatest.{ Matchers, WordSpecLike }

object PortAssignerSpec {
  implicit class AsProjectName(val name: String) extends AnyVal {
    import PortAssigner.ProjectName
    def asProjectName: ProjectName = ProjectName(name)
  }
}

class PortAssignerSpec extends WordSpecLike with Matchers {
  import PortAssignerSpec._

  private val portRange = PortRange(20000, 30000)

  "A projects' port assigner" should {
    "assign different ports for projects with different hashes" in {
      val projA = "a".asProjectName
      val projB = "b".asProjectName
      val projC = "c".asProjectName

      val projects = Seq(projA, projB, projC)
      val projectName2port = PortAssigner.computeProjectsPort(portRange, projects)

      projectName2port.values.toSet should have size 3 // no duplicates
    }

    "assign different ports for projects with the same hash" in {
      // these three projects happens to have the same hash
      val projA = "AaAa".asProjectName
      val projB = "BBBB".asProjectName
      val projC = "AaBB".asProjectName

      // verify they have indeed the same hash
      projA.name.hashCode() should be(projB.name.hashCode())
      projB.name.hashCode() should be(projC.name.hashCode())

      val projects = Seq(projA, projC, projB)
      val projectName2port = PortAssigner.computeProjectsPort(portRange, projects)

      projectName2port(projA) should not be projectName2port(projB)
      projectName2port(projA) should not be projectName2port(projC)
      projectName2port(projB) should not be projectName2port(projC)
    }

    "assign the same ports to existing project when adding a new project" in {
      val projA = "a".asProjectName
      val projC = "c".asProjectName
      val existingProjects = Seq(projA, projC)
      val existingProjectName2port = PortAssigner.computeProjectsPort(portRange, existingProjects)

      // SUT
      val projB = "b".asProjectName
      val projects = Seq(projA, projB, projC)
      val projectName2port = PortAssigner.computeProjectsPort(portRange, projects)

      projectName2port(projA) shouldBe existingProjectName2port(projA)
      projectName2port(projC) shouldBe existingProjectName2port(projC)
    }

    "wrap around when a port would overflow the port range's limit" in {
      // these three projects happens to have the same hash
      val projA = "AaAa".asProjectName
      val projB = "BBBB".asProjectName
      val projC = "AaBB".asProjectName

      // small port range to force the wrap around logic to be run
      val portRange = PortRange(7, 11)

      val projects = Seq(projA, projC, projB)
      val projectName2port = PortAssigner.computeProjectsPort(portRange, projects)

      projectName2port(projA) shouldBe Port(11)
      projectName2port(projC) shouldBe Port(7) // wrap around behavior executed!
      projectName2port(projB) shouldBe Port(8)
    }

    "assign ports first to projects with non-colliding hash/port" in {
      // Using the range is key to exercise the expected functionality. Basically, we want to check that 
      // the port assigned to `projC` cannot be affected by `projB`, which happens to have the same hash of `projA`.
      // Said otherwise, projects with non colliding hash should get their port assigned **before** projects 
      // that result in a port collision.
      val portRange = PortRange(7, 9)

      // Here we have two projects that will hash to different ports, i.e., no collisions.
      val projA = "AaAa".asProjectName
      val projC = "d".asProjectName

      val projectsWithNoCollisions = Seq(projA, projC)
      val projectName2portNoCollisions = PortAssigner.computeProjectsPort(portRange, projectsWithNoCollisions)

      // Here we check the port we expect to be assigned when only projects A and B exist
      projectName2portNoCollisions(projA) shouldBe Port(7)
      projectName2portNoCollisions(projC) shouldBe Port(8)

      // Now let's add a project that happens to have the same hash of project A (and, hence, we will have a port collision)
      val projB = "BBBB".asProjectName
      val projectsWithCollisions = Seq(projA, projB, projC)
      val projectName2portWithCollisions = PortAssigner.computeProjectsPort(portRange, projectsWithCollisions)

      // Note how project A and C have still got assigned the same port, while project B will get the next available port, counting 
      // from 7 (which is project's A port). That turns out to be port 9.
      projectName2portWithCollisions(projA) shouldBe Port(7)
      projectName2portWithCollisions(projC) shouldBe Port(8)
      projectName2portWithCollisions(projB) shouldBe Port(9)
    }

    "throw an IllegalArgumentException if the port range is smaller than the number of projects" in {
      val projA = "a".asProjectName
      val projB = "b".asProjectName

      val portRange = PortRange(1, 1)
      val projects = Seq(projA, projB)

      intercept[IllegalArgumentException] {
        PortAssigner.computeProjectsPort(portRange, projects)
      }
    }
  }
}
