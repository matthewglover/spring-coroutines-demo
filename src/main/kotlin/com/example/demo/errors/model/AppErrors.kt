package com.example.demo.errors.model

import am.ik.yavi.core.ConstraintViolations

sealed interface AppError

sealed interface IncomingDataError : AppError

data class DeserializationError(val cause: Throwable) : IncomingDataError

data class ValidationError(val className: String?, val constraintViolations: ConstraintViolations) :
  IncomingDataError {
  val violationMessages: String
    get() =
      constraintViolations
        .details()
        .map { violationDetail -> violationDetail.defaultMessage }
        .joinToString(",\n")
}

sealed interface UserRepositoryError : AppError

data class UserRetrievalError(val cause: Throwable) : UserRepositoryError {
  val message: String
    get() = "Error retrieving user: ${cause.message}"
}

data class UserCreationError(val cause: Throwable) : UserRepositoryError {
  val message: String
    get() = "Error creating user: ${cause.message}"
}


sealed interface ContextError : AppError

data class ContextCreationError(val cause: Throwable): ContextError {
  companion object
  val message: String
    get() = "Error retrieving user: ${cause.message}"
}
