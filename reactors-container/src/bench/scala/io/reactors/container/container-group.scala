package io.reactors.container



import org.scalameter.api._



class ContainerGroup extends Bench.Group {

  include(new RTileMapBench {})

}
