package br.com.itau.challenge.balance.domain.model

import java.math.BigDecimal
import java.util.UUID

object BalanceFixtures {
    val ACCOUNT_ID: UUID = UUID.fromString("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val OWNER_ID: UUID = UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a")
    val TRANSACTION_ID: UUID = UUID.fromString("8e8ae808-b154-48b5-9f3e-553935cc4543")
    const val TIMESTAMP_MICROS = 1_751_641_364_589_998L

    fun brl(amount: String): Money = Money.of(BigDecimal(amount), "BRL")

    fun transaction(
        id: UUID = TRANSACTION_ID,
        amount: Money = brl("97.07"),
        status: TransactionStatus = TransactionStatus.APPROVED,
        timestampMicros: Long = TIMESTAMP_MICROS,
    ) = Transaction(id, TransactionType.CREDIT, amount, status, EventTimestamp(timestampMicros))

    fun account(status: String = "ENABLED", balance: Money = brl("183.12")) =
        Account(ACCOUNT_ID, OWNER_ID, AccountStatus(status), balance)

    fun accountBalance(
        accountId: UUID = ACCOUNT_ID,
        timestampMicros: Long = TIMESTAMP_MICROS,
        transactionId: UUID = TRANSACTION_ID,
    ) = AccountBalance(
        accountId = accountId,
        ownerId = OWNER_ID,
        balance = brl("183.12"),
        status = AccountStatus("ENABLED"),
        updatedAt = EventTimestamp(timestampMicros),
        lastTransactionId = transactionId,
    )
}