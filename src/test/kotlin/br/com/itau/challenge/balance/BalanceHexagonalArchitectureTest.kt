package br.com.itau.challenge.balance

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class BalanceHexagonalArchitectureTest {

    private val domainFiles = Konsist.scopeFromPackage("br.com.itau.challenge.balance.domain..").files

    @Test
    fun `domain depends only on its own package and the standard library`() {
        domainFiles.assertFalse { file ->
            file.hasImport { import ->
                import.name.startsWith("br.com.itau.challenge.") &&
                    !import.name.startsWith("br.com.itau.challenge.balance.domain.")
            }
        }
    }

    @Test
    fun `domain does not depend on frameworks or infrastructure`() {
        val forbidden = listOf("org.springframework", "software.amazon", "org.apache.kafka", "t...") // TODO: lista cortada na foto

        domainFiles.assertFalse { file -> file.hasImport { import -> forbidden.any { import.name.startsWith(it) } } }
    }
}