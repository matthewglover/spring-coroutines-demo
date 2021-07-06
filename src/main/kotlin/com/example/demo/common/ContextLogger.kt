package com.example.demo.common

import com.example.demo.context.readDefaultRequestTags
import com.example.demo.context.readReactorContext
import com.example.demo.context.readRequestData
import java.util.concurrent.TimeUnit
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.StringMapMessage
import org.apache.logging.log4j.util.MessageSupplier

typealias TagProvider = () -> Tags

typealias MessageLogger = (MessageSupplier) -> Unit

typealias ErrorMessageLogger = (MessageSupplier, Throwable) -> Unit

class ContextLogger<T>(clazz: Class<T>, val currentTime: SystemTimeSupplier = System::nanoTime) {
  private val logger = LogManager.getLogger(clazz)

  suspend fun info(providedTags: TagProvider): Unit {
    if (logger.isInfoEnabled) {
      log(logger::info, providedTags)
    }
  }

  suspend fun error(providedTags: TagProvider): Unit {
    if (logger.isErrorEnabled) {
      log(logger::error, providedTags)
    }
  }

  suspend fun error(providedTags: TagProvider, throwable: Throwable): Unit {
    if (logger.isErrorEnabled) {
      log(logger::error, providedTags, throwable)
    }
  }

  private suspend fun log(logMessage: MessageLogger, providedTags: TagProvider): Unit {
    val tags = createDefaultTags() + providedTags()

    logMessage { StringMapMessage(tags) }
  }

  private suspend fun log(logMessageAndError: ErrorMessageLogger, providedTags: TagProvider, throwable: Throwable): Unit {
    val tags = createDefaultTags() + providedTags()

    logMessageAndError({ StringMapMessage(tags) }, throwable)
  }

  private suspend fun createDefaultTags(): Tags {
    val reactorContext = readReactorContext()
    val requestData = reactorContext.readRequestData()
    val defaultRequestTags = reactorContext.readDefaultRequestTags()

    val elapsedMillis = requestData.startTime.elapsedMillis(currentTime())
    val elapsedMillisTag = Tags("elapsedMillis" to elapsedMillis.toString())

    return defaultRequestTags + elapsedMillisTag
  }
}

fun Long.elapsedMillis(currentTime: Long): Long = TimeUnit.NANOSECONDS.toMillis(currentTime - this)
