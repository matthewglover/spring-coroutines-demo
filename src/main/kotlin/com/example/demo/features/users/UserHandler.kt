package com.example.demo.features.users

import am.ik.yavi.builder.ValidatorBuilder
import am.ik.yavi.builder.konstraint
import com.example.demo.common.deserializeAndValidate
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class UserHandler(private val userRepository: UserRepository) {
  companion object {
    private val newUserValidator =
        ValidatorBuilder.of<NewUser>()
            .konstraint(NewUser::name) { notBlank().greaterThan(8).lessThan(20) }
            .konstraint(NewUser::age) { greaterThanOrEqual(21) }
            .build()
  }

  suspend fun addUser(request: ServerRequest): ServerResponse {
    val newUser = deserializeAndValidate(request, newUserValidator)
    val user = userRepository.add(newUser)

    return ServerResponse.ok().bodyValueAndAwait(user)
  }

  suspend fun fetchUsers(_request: ServerRequest): ServerResponse =
      ServerResponse.ok().bodyAndAwait(userRepository.all())
}
