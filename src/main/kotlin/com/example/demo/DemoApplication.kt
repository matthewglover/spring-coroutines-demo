package com.example.demo

import am.ik.yavi.builder.ValidatorBuilder
import am.ik.yavi.builder.konstraint
import am.ik.yavi.core.ConstraintViolations
import am.ik.yavi.core.Validator
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import java.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.nativex.hint.AccessBits
import org.springframework.nativex.hint.TypeHint
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

// Reflection entry required due to how Coroutines generate bytecode with an Object return type, see
// https://github.com/spring-projects/spring-framework/issues/21546 related issue
@TypeHint(types = [User::class, NewUser::class], access = AccessBits.FULL_REFLECTION)
@ConfigurationPropertiesScan("com.example.demo")
@SpringBootApplication
class DemoApplication

fun main() {
  runApplication<DemoApplication>()
}

class ValidationError(message: String, val constraintViolations: ConstraintViolations) :
    RuntimeException(message)

class MonoValidator<T : Any>(private val validator: Validator<T>) {
  fun T.validate(): Mono<T> =
      validator
          .validateToEither(this)
          .fold(
              { constraintViolations ->
                Mono.error(
                    ValidationError(
                        validationErrorMessage(constraintViolations), constraintViolations))
              },
              { Mono.just(it) })

  private fun T.validationErrorMessage(constraintViolations: ConstraintViolations): String =
      "${this.javaClass.simpleName} is invalid. The following constraint violations were found:\n" +
          violationMessages(constraintViolations)

  private fun violationMessages(constraintViolations: ConstraintViolations): String =
      constraintViolations
          .details()
          .map { violationDetail -> violationDetail.defaultMessage }
          .joinToString(",\n")
}

inline suspend fun <reified T : Any> parseAndValidateRequest(
    request: ServerRequest,
    validator: MonoValidator<T>
): T = request.bodyToMono<T>().flatMap { validator.run { it.validate() } }.awaitSingle()

data class NewUser(val name: String, val age: Int)

@Configuration
class RouterConfig {
  @Bean
  fun route(greetHandler: GreetHandler, userHandler: UserHandler) = coRouter {
    GET("/greet", greetHandler::greet)
    POST("/user", userHandler::addUser)
    GET("/users", userHandler::fetchUsers)
  }
}

@Component
class GreetHandler {
  suspend fun greet(serverRequest: ServerRequest): ServerResponse =
      ok().bodyValueAndAwait("Hello, world!")
}

@Component
class UserHandler(private val userRepository: UserRepository) {
  companion object {
    private val newUserValidator =
        MonoValidator(
            ValidatorBuilder.of<NewUser>()
                .konstraint(NewUser::name) { notBlank().greaterThan(8).lessThan(20) }
                .konstraint(NewUser::age) { greaterThanOrEqual(21) }
                .build())
  }

  suspend fun addUser(request: ServerRequest): ServerResponse {
    try {
      val newUser = parseAndValidateRequest(request, newUserValidator)
      val user = userRepository.add(newUser)

      return ok().bodyValueAndAwait(user)
    } catch (throwable: RuntimeException) {
      return badRequest().bodyValueAndAwait(throwable.message ?: "Oops!")
    }
  }

  suspend fun fetchUsers(_request: ServerRequest): ServerResponse =
      ok().bodyAndAwait(userRepository.all())
}

@ConstructorBinding
@ConfigurationProperties(prefix = "postgres")
data class DatabaseConfigurationProperties(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String
)

@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration(private val config: DatabaseConfigurationProperties) :
    AbstractR2dbcConfiguration() {

  @Bean
  override fun connectionFactory(): ConnectionFactory {
    return PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(config.host)
            .port(config.port)
            .database(config.database)
            .username(config.username)
            .password(config.password)
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
  suspend fun add(newUser: NewUser): User {
    val (name, age) = newUser

    return r2dbcEntityTemplate
        .databaseClient
        .sql("INSERT INTO users(name, age) VALUES (:name, :age) RETURNING user_id")
        .bind("name", name)
        .bind("age", age)
        .map { row -> row.get("user_id", Integer::class.java)!!.toInt() }
        .one()
        .map { userId -> User(userId, name, age) }
        .awaitFirst()
  }

  suspend fun all(): Flow<User> =
      r2dbcEntityTemplate.select(User::class.java).from("users").all().asFlow()
}
