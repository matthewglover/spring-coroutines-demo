package com.example.demo

import am.ik.yavi.builder.ValidatorBuilder
import am.ik.yavi.builder.konstraint
import am.ik.yavi.core.ConstraintViolations
import am.ik.yavi.fn.Either
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import java.util.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@SpringBootApplication class DemoApplication

fun main() {
  runApplication<DemoApplication>()
}

class ValidationError(message: String, val constraintViolations: ConstraintViolations) :
    RuntimeException(message)

interface Validator<T> {
  fun validationErrorMessage(constraintViolations: ConstraintViolations): String

  fun T.validate(): Either<ConstraintViolations, T>

  fun T.validateToMono(): Mono<T> =
      validate()
          .fold(
              { constraintViolations ->
                Mono.error(
                    ValidationError(
                        validationErrorMessage(constraintViolations), constraintViolations))
              },
              { t -> Mono.just(t!!) })
}

inline fun <reified T : Any> parseAndValidateRequest(
    request: ServerRequest,
    validator: Validator<T>
): Mono<T> = request.bodyToMono<T>().flatMap { t -> validator.run { t.validateToMono() } }

object NewUserValidator : Validator<NewUser> {
  val validator =
      ValidatorBuilder.of<NewUser>()
          .konstraint(NewUser::name) { notBlank().lessThanOrEqual(20) }
          .konstraint(NewUser::age) { greaterThanOrEqual(21) }
          .build()

  override fun NewUser.validate(): Either<ConstraintViolations, NewUser> {
    return validator.validateToEither(this)
  }

  override fun validationErrorMessage(constraintViolations: ConstraintViolations): String {
    val violationMessages: String =
        constraintViolations
            .details()
            .map { violationDetail -> violationDetail.defaultMessage }
            .joinToString(",\n")

    return "NewUser is invalid. The following constraint violations were found:\n" + violationMessages
  }
}

data class NewUser(val name: String, val age: Int)

@Configuration
class GreetRoute {
  @Bean
  fun route(userRepository: UserRepository, userHandler: UserHandler) = router {
    GET("/greet") { _ -> ServerResponse.ok().body(Mono.just("Hello, world!"), String::class.java) }
    POST("/user", userHandler::addUser)
    GET("/users", userHandler::fetchUsers)
  }
}

@Component
class UserHandler(private val userRepository: UserRepository) {

  fun addUser(request: ServerRequest): Mono<ServerResponse> =
      parseAndValidateRequest(request, NewUserValidator)
          .flatMap(userRepository::add)
          .flatMap { user -> ServerResponse.ok().bodyValue(user) }
          .onErrorResume { error ->
            ServerResponse.badRequest().bodyValue(error.message ?: "Oops!")
          }

  fun fetchUsers(_request: ServerRequest): Mono<ServerResponse> =
      ServerResponse.ok().body(userRepository.all(), User::class.java)
}

@Configuration(proxyBeanMethods=false)
class DatabaseConfiguration : AbstractR2dbcConfiguration() {

  @Bean
  override fun connectionFactory(): ConnectionFactory {
    return PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host("localhost")
            .port(5433)
            .database("devdb")
            .username("devdb")
            .password("devpassword")
            .build())
  }

  @Bean
  override fun r2dbcMappingContext(
      namingStrategy: Optional<NamingStrategy?>,
      r2dbcCustomConversions: R2dbcCustomConversions
  ): R2dbcMappingContext {
    Assert.notNull(namingStrategy, "NamingStrategy must not be null!")
    val context = R2dbcMappingContext(namingStrategy.orElse(NamingStrategy.INSTANCE)!!)
    context.setSimpleTypeHolder(r2dbcCustomConversions.simpleTypeHolder)
    return context
  }
}

data class User(@Id val userId: Int, val name: String, val age: Int)

@Component
class UserRepository(private val r2dbcEntityTemplate: R2dbcEntityTemplate) {
  fun add(newUser: NewUser): Mono<User> {
    val (name, age) = newUser

    return r2dbcEntityTemplate
        .databaseClient
        .sql("INSERT INTO users(name, age) VALUES (:name, :age) RETURNING user_id")
        .bind("name", name)
        .bind("age", age)
        .map { row -> row.get("user_id", Integer::class.java)!!.toInt() }
        .one()
        .map { userId -> User(userId, name, age) }
  }

  fun all(): Flux<User> = r2dbcEntityTemplate.select(User::class.java).from("users").all()
}
