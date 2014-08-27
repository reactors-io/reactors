

## Event sourcing

### Akka

Events stored in order into the persistent store:

    persist(object) { evt =>
      // ...
    }

Persistence drivers - local file system, Cassandra, DynamoDB, MongoDB, ...

   def receivePersisten = ... // called as messages arrive

   def receiveEvent = ... // called after recovery

Additionally - snapshotting! Every x seconds, or y messages, you compute and store the snapshot.


## Multiple channels

Arbitrary number of channels per isolate, to allow it to receive different message types.
