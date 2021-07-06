package com.example.demo.features.users

import com.example.demo.errors.model.UserCreationError
import com.example.demo.errors.model.UserRetrievalError
import com.example.demo.errors.wrapErrors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Component

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
