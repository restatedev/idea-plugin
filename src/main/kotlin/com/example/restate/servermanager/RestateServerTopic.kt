package com.example.restate.servermanager

import com.intellij.util.messages.Topic

/**
 * Topic for Restate server events.
 */
interface RestateServerTopic {
  companion object {
    val TOPIC = Topic.create("Restate Server Events", RestateServerTopic::class.java)
  }

  /**
   * Called when the server has started and is ready to accept connections.
   */
  fun onServerStarted()

  /**
   * Called when the server has stopped.
   */
  fun onServerStopped()
}