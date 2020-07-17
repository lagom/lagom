/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode

import scala.collection.immutable

object PortAssigner {
  private[lagom] case class ProjectName(name: String) {
    def withTls = ProjectName(name + "-tls")
  }
  private[lagom] object ProjectName {
    implicit object OrderingProjectName extends Ordering[ProjectName] {
      def compare(x: ProjectName, y: ProjectName): Int = x.name.compare(y.name)
    }
  }

  private[lagom] case class PortRange(min: Int, max: Int) {
    require(min > 0, "Bottom port range must be greater than 0")
    require(max < Integer.MAX_VALUE, "Upper port range must be smaller than " + Integer.MAX_VALUE)
    require(min <= max, "Bottom port range must be smaller than the upper port range")

    val delta: Int                    = max - min + 1
    def includes(value: Int): Boolean = value >= min && value <= max
  }

  private[lagom] object Port {
    final val Unassigned = Port(-1)
  }

  private[lagom] case class Port(value: Int) extends AnyVal {
    def next: Port = Port(value + 1)
  }

  def computeProjectsPort(
      range: PortRange,
      projectNames: Seq[ProjectName],
      enableSsl: Boolean
  ): Map[ProjectName, Port] = {
    val lagomProjects = projectNames.to[immutable.SortedSet]

    val projects =
      // duplicate the project list by adding the tls variant
      if (enableSsl) lagomProjects.flatMap { plainName =>
        Seq(plainName, plainName.withTls)
      } else lagomProjects

    val doubleMessage =
      if (enableSsl) "The number of ports available must be at least twice the number of projects."
      else ""

    require(
      projects.size <= range.delta,
      s"""A larger port range is needed, as you have ${lagomProjects.size} Lagom projects and only ${range.delta}
         |ports available. $doubleMessage
         |You should increase the range passed for the lagomPortRange build setting.
       """.stripMargin
    )

    @annotation.tailrec
    def findFirstAvailablePort(port: Port, unavailable: Set[Port]): Port = {
      // wrap around if the port's number equal the portRange max limit
      if (!range.includes(port.value)) findFirstAvailablePort(Port(range.min), unavailable)
      else if (unavailable(port)) findFirstAvailablePort(port.next, unavailable)
      else port
    }

    @annotation.tailrec
    def assignProjectPort(
        projectNames: Seq[ProjectName],
        assignedPort: Set[Port],
        unassigned: Vector[ProjectName],
        result: Map[ProjectName, Port]
    ): Map[ProjectName, Port] =
      projectNames match {
        case Nil if unassigned.nonEmpty =>
          // if we are here there are projects with colliding hash that still need to get their port assigned. As expected, this step is carried out after assigning
          // a port to all non-colliding projects.
          val proj          = unassigned.head
          val projectedPort = projectedPortFor(proj)
          val port          = findFirstAvailablePort(projectedPort, assignedPort)
          assignProjectPort(projectNames, assignedPort + port, unassigned.tail, result + (proj -> port))
        case Nil => result
        case proj +: rest =>
          val projectedPort = projectedPortFor(proj)
          if (assignedPort(projectedPort)) assignProjectPort(rest, assignedPort, unassigned :+ proj, result)
          else assignProjectPort(rest, assignedPort + projectedPort, unassigned, result + (proj -> projectedPort))
      }

    def projectedPortFor(name: ProjectName): Port = {
      val hash      = Math.abs(name.hashCode())
      val portDelta = hash % range.delta
      Port(range.min + portDelta)
    }

    assignProjectPort(projects.toSeq, Set.empty[Port], Vector.empty[ProjectName], Map.empty[ProjectName, Port])
  }
}
