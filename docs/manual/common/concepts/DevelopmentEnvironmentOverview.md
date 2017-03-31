# Development environment overview

Setting up a Lagom development environment takes only minutes using an sbt template or Maven archetype as described in [[Getting Started with Lagom|IntroGetStarted]]. Without any coding, scripting or configuration on your part, the templates set up the following:

* A hierarchical build structure with two example services. You can follow their pattern to begin creating your own services, right in the same project.

* The infrastructure necessary to run and test your services locally:
    
    * A [[Kafka server|KafkaServer]] for handling messages. 

    * A [[Cassandra server|CassandraServer]] for handling persistence.  

    * A [[Service Registry and Service Gateway|ServiceLocator]] to support location transparency. 

* The configuration necessary to build and run the services and infrastructure.

After you have created a project using one of these templates, the `runAll` command starts all of the runtime components and you can test immediately. 

<!---The following illustrates the runtime infrastructure that comes out-of-the-box with the Lagom development environment. (diagram see slides 1-3) --->







