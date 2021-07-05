package com.example.demo

import am.ik.yavi.builder.ValidatorBuilder
import am.ik.yavi.builder.konstraint
import am.ik.yavi.core.ConstraintViolations
import am.ik.yavi.core.Validator
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.StringMapMessage
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.RequestPath
import org.springframework.nativex.hint.AccessBits
import org.springframework.nativex.hint.TypeHint
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.*
import reactor.util.context.Context

// Reflection entry required due to how Coroutines generate bytecode with an Object return type, see
// https://github.com/spring-projects/spring-framework/issues/21546 related issue
@TypeHint(types = [User::class, NewUser::class], access = AccessBits.FULL_REFLECTION)
@ConfigurationPropertiesScan("com.example.demo")
@SpringBootApplication
class DemoApplication

fun main() {
  runApplication<DemoApplication>()
}

inline suspend fun <reified T : Any> deserializeAndValidate(
    request: ServerRequest,
    validator: Validator<T>
): T {
  val deserializedBody = request.awaitBody<T>()

  val result = validator.validate(deserializedBody)

  if (result.isValid) {
    return deserializedBody
  } else {
    throw AppException(ValidationError(T::class.simpleName, result))
  }
}

data class NewUser(val name: String, val age: Int)

@Configuration
class RouterConfig {
  @Bean
  fun route(greetHandler: GreetHandler, userHandler: UserHandler, createContext: CreateContext) =
      coRouter {
    GET("/greet", greetHandler::greet)
    POST("/user", userHandler::addUser)
    GET("/users", userHandler::fetchUsers)

    filter(createContext::filter)
    filter(CreateDefaultTags::filter)
    filter(LogRequest::filter)
    filter(AppErrorResponseMapper::filter)
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
        ValidatorBuilder.of<NewUser>()
            .konstraint(NewUser::name) { notBlank().greaterThan(8).lessThan(20) }
            .konstraint(NewUser::age) { greaterThanOrEqual(21) }
            .build()
  }

  suspend fun addUser(request: ServerRequest): ServerResponse {
    val newUser = deserializeAndValidate(request, newUserValidator)
    val user = userRepository.add(newUser)

    return ok().bodyValueAndAwait(user)
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
    return wrapErrors(
        ::UserCreationError,
        suspend {
          val (name, age) = newUser

          r2dbcEntityTemplate
              .databaseClient
              .sql("INSERT INTO users(name, age) VALUES (:name, :age) RETURNING user_id")
              .bind("name", name)
              .bind("age", age)
              .map { row -> row.get("user_id", Integer::class.java)!!.toInt() }
              .one()
              .map { userId -> User(userId, name, age) }
              .awaitFirst()
        })
  }

  suspend fun all(): Flow<User> =
      wrapErrors(::UserRetrievalError) {
        r2dbcEntityTemplate.select(User::class.java).from("users").all().asFlow()
      }
}


typealias HandlerFunction = suspend (ServerRequest) -> ServerResponse

typealias HandlerFilterFunction = suspend (ServerRequest, HandlerFunction) -> ServerResponse

typealias SystemTimeSupplier = () -> Long

data class RequestData(val startTime: Long, val method: HttpMethod?, val requestPath: RequestPath)

@Component
class CreateContext(private val currentTime: SystemTimeSupplier = System::nanoTime) {
  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    val requestData = toRequestData(serverRequest)
    val updatedContext = writeToReactorContext(requestData)

    return withContext(updatedContext) { next(serverRequest) }
  }

  private fun toRequestData(serverRequest: ServerRequest): RequestData =
      RequestData(
          startTime = currentTime(),
          method = serverRequest.method(),
          requestPath = serverRequest.requestPath())
}

object CreateDefaultTags {
  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    val reactorContext = readReactorContext()
    val requestData = reactorContext.readRequestData()

    val defaultRequestTags = toDefaultRequestTags(requestData)
    val updatedContext = writeToReactorContext(defaultRequestTags)

    return withContext(updatedContext) { next(serverRequest) }
  }
}

object LogRequest {
  private val logger = ContextLogger<LogRequest>(LogRequest::class.java)

  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {

    logger.info { Tags("starting" to "now") }

    val serverResponse = next(serverRequest)

    logger.info { Tags("completed" to "done") }

    return serverResponse
  }
}

object AppErrorResponseMapper {

  suspend fun filter(serverRequest: ServerRequest, next: HandlerFunction): ServerResponse {
    try {
      return next(serverRequest)
    } catch (throwable: RuntimeException) {
      if (throwable is AppException) {
        val appError = throwable.appError

        return when (appError) {
          is IncomingDataError ->
              when (appError) {
                is DeserializationError ->
                    badRequest().bodyValueAndAwait(appError.cause.localizedMessage)
                is ValidationError -> badRequest().bodyValueAndAwait(appError.violationMessages)
              }
          is UserRepositoryError ->
              when (appError) {
                is UserRetrievalError ->
                    status(HttpStatus.BAD_GATEWAY).bodyValueAndAwait(appError.message)
                is UserCreationError ->
                    status(HttpStatus.BAD_GATEWAY).bodyValueAndAwait(appError.message)
              }
          is ContextError ->
            when (appError) {
              is ContextCreationError ->
                status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValueAndAwait(appError.message)
            }
        }
      } else {
        return status(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyValueAndAwait(throwable.message ?: "Oops!")
      }
    }
  }
}

data class Tags(private val map: PersistentMap<String, String>) :
    PersistentMap<String, String> by map {
  constructor(vararg pairs: Pair<String, String>) : this(persistentMapOf(*pairs))
}

fun toDefaultRequestTags(requestData: RequestData): Tags =
    Tags(
        "method" to requestData.method.toString(),
        "path" to requestData.requestPath.toString(),
    )

fun Tags.withElapsedMillis(elapsedMillis: Long): Tags =
    Tags(put("elapsedMillis", elapsedMillis.toString()))

typealias NativeReactorContext = Context

suspend fun readReactorContext(): NativeReactorContext =
    coroutineContext[ReactorContext]?.context
        ?: throw RuntimeException("ReactorContext not availabe")

inline suspend fun <reified T : Any> writeToReactorContext(data: T): CoroutineContext =
    readReactorContext().put(T::class, data).asCoroutineContext()

fun Context.readRequestData(): RequestData =
    SimpleProvider {
      throw RuntimeException("ReqeustData read error")
//      getOrEmpty<RequestData>(RequestData::class).orElseThrow {
//        RuntimeException("RequestData read error")
//      }
    }.wrapError(::ContextCreationError)

fun Context.readDefaultRequestTags(): Tags =
    getOrEmpty<Tags>(Tags::class).orElseThrow { RuntimeException("DefaultRequestTags read error") }

typealias TagProvider = () -> PersistentMap<String, String>

class ContextLogger<T>(clazz: Class<T>, val currentTime: SystemTimeSupplier = System::nanoTime) {
  private val logger = LogManager.getLogger(clazz)

  suspend fun info(providedTags: TagProvider): Unit {
    if (logger.isInfoEnabled) {
      val reactorContext = readReactorContext()
      val requestData = reactorContext.readRequestData()
      val defaultRequestTags = reactorContext.readDefaultRequestTags()

      val elapsedMillis = requestData.startTime.elapsedMillis(currentTime())
      val elapsedMillisTag = Tags("elapsedMillis" to elapsedMillis.toString())

      logger.info { StringMapMessage(defaultRequestTags + elapsedMillisTag + providedTags()) }
    }
  }
}

fun Long.elapsedMillis(currentTime: Long): Long = TimeUnit.NANOSECONDS.toMillis(currentTime - this)

class AppException(val appError: AppError) : RuntimeException()

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
  val message: String
    get() = "Error retrieving user: ${cause.message}"
}

fun interface SuspendedProvider<T> {
  suspend fun provide(): T
}

suspend fun <T> SuspendedProvider<T>.wrapError(toAppError: (Throwable) -> AppError): T {
  return try {
    this.provide()
  } catch (throwable: Throwable) {
    throw AppException(toAppError(throwable))
  }
}

fun interface SimpleProvider<T> {
  fun provide(): T
}

fun <T> SimpleProvider<T>.wrapError(toAppError: (Throwable) -> AppError): T {
  return try {
    this.provide()
  } catch (throwable: Throwable) {
    throw AppException(toAppError(throwable))
  }
}



suspend fun <T> wrapErrors(
  appErrorProvider: (Throwable) -> AppError,
  resultProvider: suspend () -> T
): T {
  return try {
    resultProvider()
  } catch (throwable: Throwable) {
    throw AppException(appErrorProvider(throwable))
  }
}
