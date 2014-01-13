## Introduction




## Reactives

- reactives
- reactors
- reactive never
- reactive emitters

### Subscriptions

- subscriptions
- weak subscriptions

### Composing reactives

- functional combinators: `filter`, `map`, `takeWhile`, `dropWhile`, `takeFirst`, `takeLast`, `either`
- the `foldPast` combinator

### Signals

- signals -- reactives with the last value cached
- signal constant
- mutable signal -- the event adapter pattern
- aggregate signal
- `zip`, `reducePast`, `changed`
- the `mutation` and `update`

### Nested combinators

- `flatten`
- `mux`
- `concat`

## Reactive containers

- queries == signals, signals == queries
- inserts/removes streams + reactive builders => generic reactive bulk operations
- streams + scalar return type queries === signal return type queries, the two are equivalent
- reactive sequences
- reactive queues
- reactive sets and maps
- functional combinators on reactive sets, lazy combinators
- reactive quad tries, when reactive return types are more efficient than queries
- reactive aggregates

## Concurrency

- concurrent emitters
- isolates
- isolate combinators

## Use case -- real time 3d game engine

- architecture
- images
- performance
- GC related story

## Related work

- Rx -- lack of ordering on event streams
- ScalaReact -- weak subscriptions
- Conal Elliott, Paul Hudak -- behaviours and signals
- Elm -- foldPast

## Conclusion
