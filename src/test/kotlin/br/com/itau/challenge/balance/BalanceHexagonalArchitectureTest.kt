package br.com.itau.challenge.balance

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class BalanceHexagonalArchitectureTest {

    private val scope = Konsist.scopeFromPackage("br.com.itau.challenge.balance..")

    private val domain = Layer("Domain", "..balance.domain..")
    private val port = Layer("Port", "..balance.port..")
    private val adapter = Layer("Adapter", "..balance.adapter..")

    @Test
    fun `hexagonal layers respect dependency direction`() {
        scope.assertArchitecture {
            domain.dependsOnNothing()
            port.dependsOn(domain)
            port.doesNotDependOn(adapter)
        }
    }

    @Test
    fun `balance context does not depend on the hello example`() {
        scope.files.assertFalse { file -> file.hasImport { it.name.startsWith("br.com.itau.chal...") } } // TODO: linha cortada na foto
    }

    @Test
    fun `domain does not depend on frameworks or infrastructure`() {
        val forbidden = listOf("org.springframework", "software.amazon", "org.apache.kafka", "t...") // TODO: lista cortada na foto

        Konsist
            .scopeFromPackage("br.com.itau.challenge.balance.domain..")
            .files
            .assertFalse { file -> file.hasImport { import -> forbidden.any(import.name::startsWith) } }
    }
}