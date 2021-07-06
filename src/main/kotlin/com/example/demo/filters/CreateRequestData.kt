package com.example.demo.filters

import com.example.demo.common.HandlerFunction
import com.example.demo.common.SystemTimeSupplier
import com.example.demo.context.writeToReactorContext
import kotlinx.coroutines.withContext
import org.springframework.http.HttpMethod
import org.springframework.http.server.RequestPath
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

data class RequestData(val startTime: Long, val method: HttpMethod?, val requestPath: RequestPath)

@Component
class CreateRequestData(private val currentTime: SystemTimeSupplier = System::nanoTime) {
  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    val requestData = toRequestData(serverRequest)
    val updatedContext = writeToReactorContext(requestData)

    return withContext(updatedContext) { next(serverRequest) }
  }

  private fun toRequestData(serverRequest: ServerRequest): RequestData =
    RequestData(
      startTime = currentTime(),
      method = serverRequest.method(),
      requestPath = serverRequest.requestPath()
    )
}
