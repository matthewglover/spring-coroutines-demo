package com.example.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

class UserHandlerTest {

  @Test
  fun `simple test or route greet`() {
    val routerConfig = RouterConfig()
    val greetHandler = GreetHandler()
    val userRepository = mock<UserRepository> {}
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler)

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
    val userRepository = mock<UserRepository> {
      onBlocking { all() }.doReturn(flow {
        emit(User(1, "name", 10))
      })
    }
    val userHandler = UserHandler(userRepository)
    val routerFunction = routerConfig.route(greetHandler, userHandler)

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
