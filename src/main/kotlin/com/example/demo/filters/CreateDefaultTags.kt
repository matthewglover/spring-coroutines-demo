package com.example.demo.filters

import com.example.demo.common.HandlerFunction
import com.example.demo.common.Tags
import com.example.demo.context.readReactorContext
import com.example.demo.context.readRequestData
import com.example.demo.context.writeToReactorContext
import kotlinx.coroutines.withContext
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

object CreateDefaultTags {
  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    val reactorContext = readReactorContext()
    val requestData = reactorContext.readRequestData()

    val defaultRequestTags = toDefaultRequestTags(requestData)
    val updatedContext = writeToReactorContext(defaultRequestTags)

    return withContext(updatedContext) { next(serverRequest) }
  }
}

fun toDefaultRequestTags(requestData: RequestData): Tags =
  Tags(
    "method" to requestData.method.toString(),
    "path" to requestData.requestPath.toString(),
  )

