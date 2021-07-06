package com.example.demo.context

import com.example.demo.common.Tags
import com.example.demo.errors.SimpleProvider
import com.example.demo.errors.model.ContextCreationError
import com.example.demo.errors.wrapError
import com.example.demo.filters.RequestData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import reactor.util.context.Context

typealias NativeReactorContext = Context

suspend fun readReactorContext(): NativeReactorContext =
  coroutineContext[ReactorContext]?.context
    ?: throw RuntimeException("ReactorContext not availabe")

inline suspend fun <reified T : Any> writeToReactorContext(data: T): CoroutineContext =
  readReactorContext().put(T::class, data).asCoroutineContext()

fun NativeReactorContext.readRequestData(): RequestData =
  SimpleProvider {
    getOrEmpty<RequestData>(RequestData::class).orElseThrow {
      RuntimeException("RequestData read error")
    }
  }.wrapError(::ContextCreationError)

fun NativeReactorContext.readDefaultRequestTags(): Tags =
  getOrEmpty<Tags>(Tags::class).orElseThrow { RuntimeException("DefaultRequestTags read error") }
