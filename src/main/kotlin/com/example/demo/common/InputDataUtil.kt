package com.example.demo.common

import am.ik.yavi.core.Validator
import com.example.demo.errors.AppException
import com.example.demo.errors.model.ValidationError
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBody

inline suspend fun <reified T : Any> deserializeAndValidate(
  request: ServerRequest,
  validator: Validator<T>
): T {
  val deserializedBody = request.awaitBody<T>()

  val constraintViolations = validator.validate(deserializedBody)

  if (!constraintViolations.isValid) {
    throw AppException(ValidationError(T::class.simpleName, constraintViolations))
  }

  return deserializedBody
}
