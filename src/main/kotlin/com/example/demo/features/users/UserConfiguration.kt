package com.example.demo.features.users

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import java.util.Optional
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.util.Assert


@ConstructorBinding
@ConfigurationProperties(prefix = "postgres")
data class DatabaseConfigurationProperties(
  val host: String,
  val port: Int,
  val database: String,
  val username: String,
  val password: String
)

@Configuration
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
