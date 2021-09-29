package com.example.demo.features.greet

import com.example.demo.filters.RequestData
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.server.RequestPath
import reactor.util.context.Context
import kotlin.coroutines.CoroutineContext


internal class ContextReadingServiceTest {

    private val requestData = RequestData(5L, HttpMethod.GET, RequestPath.parse("/foo/bar", null))

    @Test
    fun `reads start time from context`() = runBlocking(reactorContextWith(requestData)) {
        val contextReadingService = ContextReadingService()

        assertEquals("The start time is: 5", contextReadingService.readStartTimeFromContext())
    }

    private fun reactorContextWith(requestData: RequestData): CoroutineContext =
        ReactorContext(Context.of(RequestData::class, requestData))
}