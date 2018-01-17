# Using immutable objects

<!--- The information on this page on how to implement immutables will move and/or change as a result of https://github.com/lagom/lagom/issues/592 -->
An immutable object cannot be modified after it was created. Immutable objects have two great advantages:

* Code based on immutable objects is clearer and likelier to be correct. Bugs involving unexpected changes simply can't occur.
* Multiple threads can safely access immutable objects concurrently.

In Lagom, immutable objects are required in several places, such as:

* Service request and response types
* Persistent entity commands, events, and states
* Publish and subscribe messages

Lagom doesn't care how you define immutable objects. You can write out the constructor and getters by hand, but we recommend using third party tools to generate them instead. Using a third party tool, such as the [Immutables](https://immutables.github.io) tool or [Lombok](https://projectlombok.org/index.html), is easier and less error-prone than coding by hand and the resulting code is shorter and easier to read.

The following sections provide more information on immutables:

* [Mutable and immutable examples](#Mutable-and-immutable-examples)
* [Lombok example](#Lombok-example)
* [Immutables tool example](#Immutables-tool-example)

## Mutable and immutable examples
In the following example of a mutable object, the setter methods can be used to modify the object after construction:

@[mutable](code/docs/home/immutable/MutableUser.java)

In contrast, in the following example of an immutable object, all fields are final and assigned at construction time (it contains no setter methods).

@[immutable](code/docs/home/immutable/ImmutableUser.java)

## Easier immutability

## Lombok example

The following example shows the definition of a `User` implemented with Lombok. Lombok handles the following details for you. It:

* modifies fields to be `private` and `final`
* creates getters for each field
* creates correct `equals`, `hashCode` and a human-friendly `toString`
* creates a constructor requiring all fields.

@[lombok-immutable](code/docs/home/immutable/LombokUser.java)

The example does not demonstrate other useful Lombok feature like `@Builder` or `@Wither` which will help you create builder and copy methods. Be aware that Lombok is not an immutability library but a code generation library which means some setups might not create immutable objects. For example, Lombok's `@Data` is equivalent to Lombok's `@Value` but will also synthesize mutable methods. Don't use Lombok's `@Data` when creating immutable classes.


### Adding Lombok to a Maven project
To add Lombok to a Maven project, declare it as a simple dependency:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.16.12</version>
</dependency>
```

### Adding Lombok to an sbt projects
For sbt, add the following line to ????

@[lagom-immutables-lombok](code/lagom-immutables.sbt)

### Integrating Lombok with an IDE
Lombok integrates with popular IDEs:
* To use Lombok in IntelliJ IDEA you'll need the [Lombok Plugin for IntelliJ IDEA](https://plugins.jetbrains.com/idea/plugin/6317-lombok-plugin) and you'll also need to enable Annotation Processing (`Settings / Build,Execution,Deployment / Compiler / Annotation Processors` and tick `Enable annotation processing`)
* To Use Lombok in Eclipse, run `java -jar lombok.jar` (see the video at [Project Lombok](https://projectlombok.org/)).


## Immutables tool example

Here is the corresponding definition of a `User` (like the above `ImmutableUser`) using Immutables:

@[immutable](code/docs/home/immutable/AbstractUser.java)

Immutables generates for you:

* builders (very convenient when your class has many fields)
* correct implementations of `equals`, `hashCode`, `toString`
* copy methods to make new instances based on old ones, e.g. `withEmail`

### Adding Immutables to a Maven project

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-immutables_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

### Adding Immutables to an sbt project

@[lagom-immutables](code/lagom-immutables.sbt)

### Integrating Immmutables with an IDE

Immutables integrates with popular IDEs. Follow the instructions for [[Eclipse|ImmutablesInIDEs#Eclipse]] or [[IntelliJ IDEA|ImmutablesInIDEs#IntelliJ-IDEA]] to add the Immutables annotation processor to your IDE.


## Collections

If an immutable object contains a collection, that collection must be immutable too.

Here is an example of an object with a mutable collection in it:

@[mutable](code/docs/home/immutable/MutableUser2.java)

The object might look like immutable since it only has final fields and no setters, but the `List` of `phoneNumbers` is mutable, because it can be changed by code like `user.getPhoneNumbers().add("+468123456")`.

One possible fix would be to make a defensive copy of the `List` in the constructor and use `Collections.unmodifiableList` in the getter, like this:

@[immutable](code/docs/home/immutable/ImmutableUser2.java)

But it is easy to make mistakes when coding this way, so we again recommend letting Immutable handle it for you, as follows.

Here is a new definition of a `User2`, like the above `ImmutableUser2`:

@[immutable](code/docs/home/immutable/AbstractUser2.java)

The `getPhoneNumbers` method will return an instance of `com.google.common.collect.ImmutableList`.

### Guava and PCollections

As seen above, Immutables uses [Guava immutable collections](https://github.com/google/guava/wiki/ImmutableCollectionsExplained) by default.

The Guava collections are certainly better for this purpose than plain `java.util` collections. However, the Guava collections are cumbersome and inefficient for some common operations (for example, making a slightly modified copy of an existing collection).

Therefore, we recommend [PCollections](https://pcollections.org/), which is a collection library that is designed from the ground up for immutability and efficiency.

This is how the above example looks like using a PCollection:

@[immutable](code/docs/home/immutable/AbstractUser3.java)

This is how to define an optional collection initialized with the empty collection:

@[immutable](code/docs/home/immutable/AbstractUser4.java)

### "Persistent" collections

There are two different and potentially confusing usages of the word "persistent" with respect to data.

You will see references, in the PCollections documentation and elsewhere, to ["persistent" data structures](https://en.wikipedia.org/wiki/Persistent_data_structure). There, the word "persistent" means that even when you construct a modified copy of a collection, the original "persists".

In the rest of this documentation, "persistent" refers instead to [persistent storage](https://en.wikipedia.org/wiki/Persistence_%28computer_science%29), as in [[Persistent Entities|PersistentEntity]] and the examples below.

### Further reading

The Immutables documentation has more details on immutable collections [here](https://immutables.github.io/immutable.html#array-collection-and-map-attributes).
