package com.example.demo.errors

import com.example.demo.errors.model.AppError

class AppException(val appError: AppError) : RuntimeException()

fun interface SimpleProvider<T> {
  fun provide(): T
}

fun <T> SimpleProvider<T>.wrapError(toAppError: (Throwable) -> AppError): T {
  return try {
    this.provide()
  } catch (throwable: Throwable) {
    throw AppException(toAppError(throwable))
  }
}

suspend fun <T> wrapErrors(
  appErrorProvider: (Throwable) -> AppError,
  resultProvider: suspend () -> T
): T {
  return try {
    resultProvider()
  } catch (throwable: Throwable) {
    throw AppException(appErrorProvider(throwable))
  }
}

fun interface SuspendedProvider<T> {
  suspend fun provide(): T
}

suspend fun <T> SuspendedProvider<T>.wrapError(toAppError: (Throwable) -> AppError): T {
  return try {
    this.provide()
  } catch (throwable: Throwable) {
    throw AppException(toAppError(throwable))
  }
}
