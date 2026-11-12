package br.com.itau.challenge.balance.infra

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.testcontainers.redpanda.RedpandaContainer

private const val REDPANDA_IMAGE = "docker.redpanda.com/redpandadata/redpanda:v26.1.14"
private const val TRANSACTIONS_PARTITIONS = 6

/** Starts a Redpanda container once per JVM and creates the topics used by the application. */
class RedpandaInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of("spring.kafka.bootstrap-servers=$bootstrapServers").applyTo(applicationContext.environment)
        createTopics(applicationContext.environment)
    }

    private fun createTopics(environment: Environment) {
        val topics = listOf(
            NewTopic(environment.getRequiredProperty("transactions.topic-name"), TRANSACTIONS_PARTITIONS, 1),
            NewTopic(environment.getRequiredProperty("transactions.dlt-topic-name"), TRANSACTIONS_PARTITIONS, 1),
            NewTopic(environment.getRequiredProperty("greeting-templates.topic-name"), 1, 1),
        )
        Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { admin ->
            val existing = admin.listTopics().names().get()
            admin.createTopics(topics.filterNot { it.name() in existing }).all().get()
        }
    }

    private companion object {
        val bootstrapServers: String by lazy {
            RedpandaContainer(REDPANDA_IMAGE).apply { start() }.bootstrapServers
        }
    }
}