# Lagom build philosophy

You are free to combine all your services in a single build, or build them individually.

Either way, Lagom allows you to use either sbt or Maven as a build tool and development environment.

## Builds: one vs. many

Lagom allows putting many services into one build.  At the other extreme, you could have one build per service.  Both approaches have advantages.

### One build

The advantage of one build is that often new features require simultaneous development on multiple services.  Having those services in the same build makes for a frictionless development experience.

The disadvantage of one build is that as the number of developers working on that build grows, developers can get in each other's way, especially if multiple teams are involved.

### Many builds

On the other hand, if each service is in its own build, each service can be changed independently. When changes culminate in a release, dependent services can upgrade and get the changes.  Each service can move forward faster because each build is isolated.

When a system includes multiple builds, in order to work with services from other builds, you'll need to do two things:

* publish build products downstream
* depend on build products from upstream builds

[[Splitting a system into multiple builds|MultipleBuilds]] describes this in more detail.

### Our recommendation

We recommend when using Lagom to take a pragmatic approach and have one build per team. Dividing builds by team limits interference with each other's work. Within a team, coordination is relatively easy, so multiple builds would just slow the team down.

As your system evolves and your organization evolves, your approach to dividing builds may need to change, too.  Services may grow and need to be split, and so may teams. With this approach, it's important not be afraid to refactor your builds to keep up with your organizational needs.
