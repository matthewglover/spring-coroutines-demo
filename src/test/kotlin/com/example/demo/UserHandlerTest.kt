package com.example.demo

import com.example.demo.features.greet.GreetHandler
import com.example.demo.features.users.User
import com.example.demo.features.users.UserHandler
import com.example.demo.features.users.UserRepository
import com.example.demo.filters.CreateRequestData
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient

class UserHandlerTest {

  @Test
  fun `simple test or route greet`() {
    val routerConfig = RouterConfig()
    val greetHandler = GreetHandler()
    val createRequestData = CreateRequestData()
    val userRepository = mockk<UserRepository>()
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler, createRequestData)

    val client = WebTestClient.bindToRouterFunction(routerFunction).build()

    client.get()
      .uri("/greet")
      .exchange()
      .expectStatus()
      .isOk
  }

  @Test
  fun `simple test or route get users`() {
    val routerConfig = RouterConfig()
    val greetHandler = GreetHandler()
    val createRequestData = CreateRequestData()
    val userRepository = mockk<UserRepository>()
    coEvery { userRepository.all() } returns flow {
        emit(User(1, "name", 10))
    }
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler, createRequestData)

    val client = WebTestClient.bindToRouterFunction(routerFunction).build()

    client.get()
      .uri("/users")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .json("""[{"userId":1,"name":"name","age":10}]""")
  }
}
