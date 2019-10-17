# Domain Modelling


## Encoding the model
  * typed protocol - Command, Event and State
  * defining typed replies and error handling
  * Enforced replies
  * Changing behavior - FSM
## EventSourcingBehaviour - glueing the bits together
  * Tagging the events - Akka Persistence Query considerations
  * Configuring snaptshots
## ClusterSharding
  * initialize it - register Behavior on ClusterSharding
  * how to use an Entity
    * looking up an instance in ClusterSharding
    * considerations on using ask pattern
  *  configuring number of shards
  *  configuring Entity passivation
## Data Serialization
