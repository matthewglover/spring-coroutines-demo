package com.example.demo

import com.example.demo.features.greet.GreetHandler
import com.example.demo.features.users.NewUser
import com.example.demo.features.users.User
import com.example.demo.features.users.UserHandler
import com.example.demo.filters.AppErrorResponseMapper
import com.example.demo.filters.CreateDefaultTags
import com.example.demo.filters.CreateRequestData
import com.example.demo.filters.LogRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.nativex.hint.AccessBits
import org.springframework.nativex.hint.TypeHint
import org.springframework.web.reactive.function.server.coRouter

// Reflection entry required due to how Coroutines generate bytecode with an Object return type, see
// https://github.com/spring-projects/spring-framework/issues/21546 related issue
@TypeHint(types = [User::class, NewUser::class], access = AccessBits.FULL_REFLECTION)
@ConfigurationPropertiesScan("com.example.demo")
@SpringBootApplication
class DemoApplication

fun main() {
  runApplication<DemoApplication>()
}

@Configuration
class RouterConfig {
  @Bean
  fun route(greetHandler: GreetHandler, userHandler: UserHandler, createRequestData: CreateRequestData) =
      coRouter {
    GET("/greet", greetHandler::greet)
    POST("/user", userHandler::addUser)
    GET("/users", userHandler::fetchUsers)

    filter(createRequestData::filter)
    filter(CreateDefaultTags::filter)
    filter(LogRequest::filter)
    filter(AppErrorResponseMapper::filter)
  }
}
