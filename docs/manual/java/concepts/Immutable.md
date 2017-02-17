# Immutable Objects

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

@[mutable](code/docs/home/immutable/MutableUser.java)

The setter methods can be used to modify the object after construction.

Here, by contrast, is an immutable object:

@[immutable](code/docs/home/immutable/ImmutableUser.java)

All fields are final and are assigned at construction time. There are no setter methods.

## Easier immutability 

Lagom doesn't care how you define your immutable objects. You can write out the constructor and getters by hand, as in the above sample.  But we recommend using third party tools to generate them instead. You can use the [Immutables](https://immutables.github.io) or [Lombok](https://projectlombok.org/index.html). Using a third party tool is easier and less error-prone than writing everything out by hand, and the resulting code is shorter and easier to read.

### Lombok

Here's a definition of a `User` implemented with Lombok:

@[lombok-immutable](code/docs/home/immutable/LombokUser.java)

That will:

 * modify fields to be `private` and `final`
 * create getters for each field
 * create correct `equals`, `hashCode` and a human-friendly `toString`
 * create a constructor requiring all fields.

This example code is not demonstrating other Lombok feature like `@Builder` or `@Wither` which will help you create builder and copy methods. Be aware that Lombok is not an immutability library but a code generation library which means some setups might not create immutable objects. For example, Lombok's `@Data` is equivalent to Lombok's `@Value` but will also synthesize mutable methods. Don't use Lombok's `@Data` when creating immutable classes.

Lombok is a simple dependency:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.16.12</version>
</dependency>
```

or in sbt:

@[lagom-immutables-lombok](code/lagom-immutables.sbt)


Lombok integrates with popular IDE's too. In order to use Lombok in IntelliJ IDEA you'll need the [Lombok Plugin for IntelliJ IDEA](https://plugins.jetbrains.com/idea/plugin/6317-lombok-plugin) and you'll also need to enable Annotation Processing (`Settings / Build,Execution,Deployment / Compiler / Annotation Processors` and tick `Enable annotation processing`). Using Lombok in Eclipse requires running `java -jar lombok.jar` (see the video at [Project Lombok](https://projectlombok.org/)).


### Immutables

Here is the corresponding definition of a `User` (like the above `ImmutableUser`) using Immutables:

@[immutable](code/docs/home/immutable/AbstractUser.java)

For free you get things like:

* builders (very convenient when your class has many fields)
* correct `equals`, `hashCode`, `toString` implementations
* copy methods to make new instances based on old ones, e.g. `withEmail`

In maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-immutables_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[lagom-immutables](code/lagom-immutables.sbt)

Immutables integrates with popular IDEs. Follow the instructions for [[Eclipse|ImmutablesInIDEs#Eclipse]] or [[IntelliJ IDEA|ImmutablesInIDEs#IntelliJ-IDEA]] to add the Immutables annotation processor to your IDE. We've found the integration with IntelliJ IDEA a bit cumbersome though.



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

Therefore, we recommend [PCollections](http://pcollections.org), which is a collection library that is designed from the ground up for immutability and efficiency.

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
