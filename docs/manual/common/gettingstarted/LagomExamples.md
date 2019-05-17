# Learning more from Lagom examples

After getting started with Lagom's Hello World example, you can learn more about Lagom by downloading and
running Lagom's "Shopping Cart" example (either [shopping-cart-scala][] or [shopping-cart-java][]).

[shopping-cart-scala]: https://github.com/lagom/lagom-samples/tree/1.5.x/shopping-cart/shopping-cart-scala
[shopping-cart-java]: https://github.com/lagom/lagom-samples/tree/1.5.x/shopping-cart/shopping-cart-java

Some of the Lagom features you can observe include:

Feature |Chirper| Online Auction |
--------|:--------:|:-------------:|
How to use persistence| Y | Y
How to use the message broker API| N | Y
How to communicate between services| Y | Y
How streaming service calls work | Y | N
How to develop a Play app with Lagom | Y | Y
How to consume Lagom services from a front-end Play app | N | Y

For focused examples, check the [lagom-samples](https://github.com/lagom/lagom-samples) repository on GitHub. Each sample describes how to achieve a particular goal, such as:

* How do I enable CORS? (using Lagom's javadsl or scaladsl)
* How do I create a Subscriber only service? (also referred to as consumer service)
* How do I use RDBMS read-sides with Cassandra write-sides? (mixed persistence in java or mixed persistence in scala)
* How to create a stateless service in Lagom for Java that uses Play's Internationalization Support.
* How do I manipulate Headers and Status Codes and test those cases?(HTTP header handling)
* How do I handle multipart/form-data file uploads? (Scala example, Java example)
* How do I use a custom message serializer and response header to implement file downloads? (Scala example)
* How do I integrate Lightbend Telemetry (Cinnamon)? (Java/Maven example)
* How do I configure the Circuit Breaker Panel for a Lagom Java application? (Java/Maven example)
* How do I deploy a Lagom Maven application in Kubernetes? (Java/Maven example)
* How do I use Lagom with Couchbase both write-side and read-side? Java Maven and Scala Sbt) (Couchbase Persistence is NOT production ready yet)
