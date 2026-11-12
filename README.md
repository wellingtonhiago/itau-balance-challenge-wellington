# Consulta de Saldos

Serviço que consome eventos de transações processadas, mantém o saldo mais recente por conta no DynamoDB e disponibiliza a consulta por HTTP.

## Arquitetura

O projeto usa arquitetura hexagonal: domínio e casos de uso dependem de portas, enquanto adaptadores integram HTTP, Kafka e DynamoDB.

```mermaid
flowchart LR
    K[Kafka / Redpanda] --> L[Listener de transações]
    L --> U[Atualização do saldo]
    L -- evento inválido --> D[DLT]
    U --> P[Porta de persistência]
    H[GET /balances/{accountId}] --> C[Controller]
    C --> G[Consulta do saldo]
    G --> P
    P --> DB[(DynamoDB)]
```

**Fluxo de escrita:** o listener consome o tópico `transacoes-financeiras-processadas`, valida o evento e persiste o saldo atual informado em `account.balance`.

**Fluxo de leitura:** `GET /balances/{accountId}` consulta o saldo armazenado e retorna os dados da conta.

## Abordagens

- A escrita condicional mantém apenas o evento mais recente; eventos antigos ou duplicados não sobrescrevem o saldo atual.
- A chave da mensagem Kafka é o `account.id`, preservando a ordem dos eventos da conta na partição.
- Mensagens inválidas são encaminhadas para `transacoes-financeiras-processadas.DLT`; falhas temporárias de storage são repetidas.
- O acesso ao DynamoDB possui timeout, retry e circuit breaker.
- Health checks e métricas Prometheus estão disponíveis pelo Actuator.

## API

`GET /balances/{accountId}` recebe um UUID canônico e retorna `id`, `owner`, `balance` e `updated_at`.

| Resposta | Situação |
|---|---|
| `200` | Saldo encontrado |
| `400` | UUID inválido |
| `404` | Conta sem saldo armazenado |
| `503` | Storage indisponível ou circuito aberto |

Exemplos de chamadas estão em [`http/balances.http`](http/balances.http). Seeds criam a tabela e os tópicos, mas não criam saldo para a conta consultada: publique primeiro um evento válido usando a chave Kafka igual ao `account.id`. O Redpanda Console local está em `http://localhost:8081`; use **Produce Message** no tópico `transacoes-financeiras-processadas` e forneça o evento no formato abaixo.

```json
{
  "transaction": {
    "id": "3c7b61e6-0c7a-4f0f-82d7-ae5d2840f192",
    "type": "CREDIT",
    "amount": 1.00,
    "currency": "BRL",
    "status": "APPROVED",
    "timestamp": 1751641364589998
  },
  "account": {
    "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
    "owner": "315e3cfe-f4af-4cd2-b298-a449e614349a",
    "created_at": "1634874339",
    "status": "ENABLED",
    "balance": { "amount": 183.12, "currency": "BRL" }
  }
}
```

Após a mensagem ser processada, consulte `http://localhost:8080/balances/5b19c8b6-0cc4-4c72-a989-0c2ee15fa975`.

## Executar localmente

Requisitos: Docker com Compose e Java 21 (para executar pela Gradle).

No PowerShell, suba os serviços de apoio e aguarde a conclusão dos seeds:

```powershell
docker compose up dynamodb dynamodb-seed dynamodb-admin redpanda redpanda-seed redpanda-console -d
docker compose wait dynamodb-seed redpanda-seed
```

Inicie a aplicação localmente:

```powershell
.\gradlew.bat bootRun
```

Para rodar a aplicação em container, em vez de `bootRun`, execute em outro terminal após os seeds:

```powershell
docker compose up --build -d app
```

Serviços locais:

| Serviço | Endereço |
|---|---|
| API | `http://localhost:8080` |
| DynamoDB Local | `http://localhost:8000` |
| DynamoDB Admin | `http://localhost:8001` |
| Redpanda Console | `http://localhost:8081` |
| Kafka / Redpanda | `localhost:19092` |

Consulte o health em `http://localhost:8080/actuator/health` e as métricas em `http://localhost:8080/actuator/prometheus`. Para acompanhar os logs dos containers use `docker compose logs -f app`. Ao terminar, pare os serviços com `docker compose down`.

> O DynamoDB Local está configurado em memória. Os dados são perdidos quando o container é removido ou reiniciado.

## Testes

Testes unitários e de arquitetura, incluindo o gate global de pelo menos 90% de cobertura de instruções:

```powershell
.\gradlew.bat check --console=plain
```

Os testes de integração usam DynamoDB Local e Redpanda reais. Suba os serviços e aguarde os seeds como na seção anterior; depois execute:

```powershell
.\gradlew.bat integrationTest --console=plain
```

Para executar somente o fluxo Kafka → DynamoDB → HTTP:

```powershell
.\gradlew.bat integrationTest --tests '*BalanceEndToEndIntegrationTest' --console=plain
```

O relatório JaCoCo fica em `build/reports/jacoco/test/html/index.html`.

## Tecnologias

Kotlin, Java 21, Spring Boot, Gradle, Kafka/Redpanda, DynamoDB, Docker Compose, JUnit 5 e JaCoCo.
