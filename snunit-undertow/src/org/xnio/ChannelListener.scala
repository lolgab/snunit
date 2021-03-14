package org.xnio

import java.nio.channels.Channel
import java.util.EventListener

trait ChannelListener[T <: Channel] extends EventListener {

}

object ChannelListener {
  trait Setter[T <: Channel] {
    def set(listener: ChannelListener[T]): Unit
  }
}
