package com.example.demo.features.greet

import com.example.demo.context.readReactorContext
import com.example.demo.context.readRequestData

class ContextReadingService {

    suspend fun readStartTimeFromContext(): String {
        val requestData = readReactorContext().readRequestData();

        return "The start time is: ${requestData.startTime}"
    }
}