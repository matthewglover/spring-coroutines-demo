package com.example.demo

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
    val createContext = CreateContext()
    val userRepository = mockk<UserRepository>()
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler, createContext)

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
    val createContext = CreateContext()
    val userRepository = mockk<UserRepository>()
    coEvery { userRepository.all() } returns flow {
        emit(User(1, "name", 10))
    }
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler, createContext)

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
