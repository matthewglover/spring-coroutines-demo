package com.example.demo.filters

import com.example.demo.common.ContextLogger
import com.example.demo.common.HandlerFunction
import com.example.demo.common.Tags
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

object LogRequest {
  private val logger = ContextLogger<LogRequest>(LogRequest::class.java)

  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {

    logger.info { Tags("starting" to "now") }

    val serverResponse = next(serverRequest)

    logger.info { Tags("completed" to "done") }

    return serverResponse
  }
}
