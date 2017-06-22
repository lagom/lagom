# Deploying scalable, resilient systems

To achieve truly reactive systems:

* Deploy your microservices in clusters so that they can scale up or down as required and remain resilient in face of network bottlenecks or failures along with hardware or software errors.
* Make sure that components on which your system relies, such as the Service Gateway and message broker do not expose a single point of failure.
* Use the capabilities of your data store to provide redundancy or replication.

<!---The following diagram shows a typical Lagom deployment. (see slide) -->

While you can deploy on the appropriate technology of your choice, Lagom supports [Lightbend Enterprise Suite](https://www.lightbend.com/platform/production) out-of-the-box. Enterprise Suite is a perfect match for Lagom, as it provides the following features:

* A way to manage configuration separately from packaged artifacts.
* Consolidated logging across many nodes.
* A supervisory system that automatically restarts services that terminate unexpectedly.
* The ability to scale up and down with ease and with speed.
* Handling of network failures, in particular those that can lead to a split brain scenario.
* Automated seed node discovery that ensures that new instances of a service join the same cluster as those already running.
* The ability to perform rolling updates of your services.
* Support for monitoring services across a cluster.
* The ability to test services locally before deploying in production.

See [[Production Overview|ProductionOverview]] and [[Reactive Platform|ReactivePlatform]] for more information.