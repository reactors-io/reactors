package io.reactors
package protocol






trait LinkProtocols {
  type Link[I, O] = (Channel[I], Events[Boolean])

  object Link {
    type Server[R, I, O] = io.reactors.protocol.Server[R, Link[I, O]]

    type Req[R, I, O] = io.reactors.protocol.Server.Req[R, Link[I, O]]
  }
}
