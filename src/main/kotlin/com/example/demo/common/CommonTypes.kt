package com.example.demo.common

import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

typealias HandlerFunction = suspend (ServerRequest) -> ServerResponse

typealias HandlerFilterFunction = suspend (ServerRequest, HandlerFunction) -> ServerResponse

typealias SystemTimeSupplier = () -> Long
