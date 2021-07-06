package com.example.demo.features.greet

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class GreetHandler {
  suspend fun greet(serverRequest: ServerRequest): ServerResponse =
      ServerResponse.ok().bodyValueAndAwait("Hello, world!")
}
