# Learning more from Lagom examples

After getting started with Hello World, you can learn more about Lagom by downloading and running one of the following examples from GitHub:

* [Chirper](https://github.com/search?utf8=%E2%9C%93&q=Lagom+chirper) demonstrates a Twitter-like application and is available for Java and Scala, with Java variations that demonstrate use of JPA and JDBC.
* [Online Auction](https://github.com/search?utf8=%E2%9C%93&q=lagom%2Fonline+auction&type=Repositories&ref=searchresults) demonstrates an eBay-like application and is also available for both Java and Scala.

Some of the Lagom features you can observe in these examples include:

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



