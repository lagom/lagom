# Using immutable objects

An immutable object is an object that cannot be modified after it was created.

Immutable objects have two great advantages:

* Code based on immutable objects is clearer and likelier to be correct. Bugs involving unexpected changes simply can't occur.
* Multiple threads can safely access immutable objects concurrently.

In Lagom, immutable objects are required in several places, such as:

* Service request and response types
* Persistent entity commands, events, and states
* Publish and subscribe messages

## Mutable vs. immutable

Here is an example of a mutable object:

@[mutable](code/docs/home/scaladsl/immutable/MutableUser.scala)

The setter methods can be used to modify the object after construction.

Here, by contrast, is an immutable object:

@[immutable](code/docs/home/scaladsl/immutable/ImmutableUser.scala)

All fields are final and are assigned at construction time. There are no setter methods.

As you can see, Scala's `case class` is very convenient for writing immutable classes.

Note that contained members of an immutable class must also be immutable. Scala has nice immutable collections that you should use in your immutable classes. Be aware of that `scala.collection.Seq` is not guaranteed to be immutable. Instead you should use `scala.collection.immutable.Seq` or concrete immutable implementations such as `List` or `Vector`.

