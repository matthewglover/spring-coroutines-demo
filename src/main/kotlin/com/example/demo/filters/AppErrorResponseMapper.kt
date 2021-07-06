package com.example.demo.filters

import com.example.demo.common.HandlerFunction
import com.example.demo.errors.AppException
import com.example.demo.errors.model.AppError
import com.example.demo.errors.model.ContextCreationError
import com.example.demo.errors.model.ContextError
import com.example.demo.errors.model.DeserializationError
import com.example.demo.errors.model.IncomingDataError
import com.example.demo.errors.model.UserCreationError
import com.example.demo.errors.model.UserRepositoryError
import com.example.demo.errors.model.UserRetrievalError
import com.example.demo.errors.model.ValidationError
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

object AppErrorResponseMapper {

  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    return try {
      next(serverRequest)
    } catch (throwable: RuntimeException) {
      mapThrowable(throwable)
    }
  }
}

suspend fun mapThrowable(throwable: Throwable): ServerResponse =
    when (throwable) {
      is AppException -> mapAppError(throwable.appError)
      else ->
          ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .bodyValueAndAwait(throwable.localizedMessage)
    }

suspend fun mapAppError(appError: AppError): ServerResponse =
    when (appError) {
      is IncomingDataError -> mapIncomingDataError(appError)
      is UserRepositoryError -> mapUserRepositoryError(appError)
      is ContextError -> mapContextError(appError)
    }

suspend fun mapContextError(contextError: ContextError): ServerResponse =
  when (contextError) {
    is ContextCreationError ->
      ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .bodyValueAndAwait(contextError.message)
  }

suspend fun mapUserRepositoryError(userRepositoryError: UserRepositoryError): ServerResponse =
  when (userRepositoryError) {
    is UserRetrievalError ->
      ServerResponse.status(HttpStatus.BAD_GATEWAY).bodyValueAndAwait(userRepositoryError.message)
    is UserCreationError ->
      ServerResponse.status(HttpStatus.BAD_GATEWAY).bodyValueAndAwait(userRepositoryError.message)
  }

suspend fun mapIncomingDataError(incomingDataError: IncomingDataError): ServerResponse =
  when (incomingDataError) {
    is DeserializationError ->
      ServerResponse.badRequest().bodyValueAndAwait(incomingDataError.cause.localizedMessage)
    is ValidationError ->
      ServerResponse.badRequest().bodyValueAndAwait(incomingDataError.violationMessages)
  }
