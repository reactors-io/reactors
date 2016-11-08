package io.reactors






/** Contains message exchange protocols and patterns.
 *
 *  Protocols inside this package are composed from basic channels and event streams.
 */
package object protocol
extends ServerProtocols
with RouterProtocols
with BackpressureProtocols
with ChannelProtocols
with Patterns
with Conversions
with Convenience
