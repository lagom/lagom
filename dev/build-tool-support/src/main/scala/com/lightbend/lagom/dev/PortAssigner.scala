/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import scala.collection.immutable

object PortAssigner {

  private[lagom] case class ProjectName(name: String) extends AnyVal
  private[lagom] object ProjectName {
    implicit object OrderingProjectName extends Ordering[ProjectName] {
      def compare(x: ProjectName, y: ProjectName): Int = x.name.compare(y.name)
    }
  }

  private[lagom] case class PortRange(min: Int, max: Int) {
    require(min > 0, "Bottom port range must be greater than 0")
    require(max < Integer.MAX_VALUE, "Upper port range must be smaller than " + Integer.MAX_VALUE)
    require(min <= max, "Bottom port range must be smaller than the upper port range")

    val delta = max - min + 1
    def includes(value: Int): Boolean = value >= min && value <= max
  }

  private[lagom] object Port {
    final val Unassigned = Port(-1)
  }

  private[lagom] case class Port(value: Int) extends AnyVal {
    def next: Port = Port(value + 1)
  }

  def computeProjectsPort(range: PortRange, projectNames: Seq[ProjectName]): Map[ProjectName, Port] = {
    val lagomProjects = projectNames.to[immutable.SortedSet]
    val mapBuilder = new Project2PortMapBuilder(range)
    mapBuilder.build(lagomProjects)
  }

  private class Project2PortMapBuilder(val range: PortRange) extends AnyVal {

    def build(projects: immutable.SortedSet[ProjectName]): Map[ProjectName, Port] = {
      require(
        projects.size <= range.delta,
        s"""A larger port range is needed, as you have ${projects.size} Lagom projects and only ${range.delta} 
             |ports available. You should increase the range passed for the 
             |lagomPortRange build setting
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
      def loop(projectNames: Seq[ProjectName], assignedPort: Set[Port], unassigned: Vector[ProjectName], result: Map[ProjectName, Port]): Map[ProjectName, Port] = projectNames match {
        case Nil if unassigned.nonEmpty =>
          // if we are here there are projects with colliding hash that still need to get their port assigned. As expected, this step is carried out after assigning 
          // a port to all non-colliding projects.
          val proj = unassigned.head
          val projectedPort = projectedPortFor(proj)
          val port = findFirstAvailablePort(projectedPort, assignedPort)
          loop(projectNames, assignedPort + port, unassigned.tail, result + (proj -> port))
        case Nil => result
        case proj +: rest =>
          val projectedPort = projectedPortFor(proj)
          if (assignedPort(projectedPort)) loop(rest, assignedPort, unassigned :+ proj, result)
          else loop(rest, assignedPort + projectedPort, unassigned, result + (proj -> projectedPort))
      }

      loop(projects.iterator.toSeq, Set.empty[Port], Vector.empty[ProjectName], Map.empty[ProjectName, Port])
    }

    private def projectedPortFor(name: ProjectName): Port = {
      val hash = Math.abs(name.hashCode())
      val portDelta = hash % range.delta
      Port(range.min + portDelta)
    }
  }
}
