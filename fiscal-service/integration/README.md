# Ambiente isolado de retry e DLQ

Execute os comandos abaixo na raiz do repositório. Não combine este arquivo com
`docker-compose.yml`. O projeto é sempre `fiscalwatch-integration`; os comandos
não encerram nem recriam os serviços `fiscalwatch-postgres` e `fiscalwatch-rabbitmq`.

## Validar e iniciar

```powershell
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml config --quiet
docker build --check --file fiscal-service/Dockerfile.integration fiscal-service
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml up --detach --build
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml ps
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml logs --tail 100 fiscal-service-it
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

O build compila os fontes atuais, inclusive alterações ainda não commitadas, em
um estágio Docker com Java 21. Não copia `target`, configurações do IntelliJ,
arquivos `.env` ou caches locais e não modifica o Java já em execução. A suíte
Java não é executada no build para evitar acesso ao banco atual. Sempre use
`--build` após mudanças nos fontes ou na configuração de integração.

O fiscal-service utiliza exclusivamente o arquivo
`/app/application-integration.yaml` via `spring.config.location`, substituindo a
configuração padrão do JAR. Flyway inicializa somente o banco `fiscalwatch_it`
no PostgreSQL isolado. As regras de retry/DLQ e a topologia da aplicação são as
mesmas do código atual. A inicialização cria a infraestrutura e as migrações
isoladas; não publica mensagens de teste nem provoca falhas.

| Serviço | Acesso pelo host | Acesso entre containers |
| --- | --- | --- |
| Fiscal-service | `http://127.0.0.1:8081` | `fiscal-service-it:8080` |
| PostgreSQL | `127.0.0.1:5434` | `postgres-it:5432` |
| RabbitMQ AMQP | `127.0.0.1:5673` | `rabbitmq-it:5672` |
| RabbitMQ Management | `http://127.0.0.1:15673` | `rabbitmq-it:15672` |

Credenciais públicas de desenvolvimento/teste:

- PostgreSQL: banco/usuário `fiscalwatch_it`, senha `integration-only-postgres`.
- RabbitMQ: usuário/vhost `fiscalwatch_it`, senha `integration-only-rabbitmq`.

Os serviços usam somente a rede bridge exclusiva `fiscalwatch-integration_integration`,
sem compartilhamento de rede com o Compose atual. As portas são expostas apenas
no loopback. Os volumes são `fiscalwatch-integration_postgres_it_data` e
`fiscalwatch-integration_rabbitmq_it_data`; nenhum volume é externo ou reutiliza
os dados atuais. Nenhuma variável do ambiente host é repassada para a aplicação.

A rede não usa `internal: true`, permitindo que o Docker publique as portas no
host. Ela mantém isolamento lógico por projeto; não bloqueia tráfego de saída.
Os bindings `127.0.0.1` continuam limitando o acesso pelas portas publicadas.

## Aplicar a correção em uma rede já criada como interna

A propriedade `internal` não é atualizada por um simples restart. Recrie a rede
e os containers exclusivamente do projeto de integração, preservando os volumes:

```powershell
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml config --quiet
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml down
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml up --detach
docker network inspect fiscalwatch-integration_integration --format '{{.Internal}}'
docker port fiscalwatch-it-fiscal-service 8080/tcp
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

Resultados esperados: `false`, `127.0.0.1:8081` e `status=UP`. O endpoint HTTP
pode levar alguns segundos para responder depois que os containers iniciarem.
Não use `--volumes`; nenhum volume precisa ser removido para corrigir a rede.

## Encerrar

```powershell
docker compose --project-name fiscalwatch-integration --file docker-compose.integration.yml down
```

Esse comando remove apenas os containers e a rede do projeto de integração,
preservando seus volumes. Não use `--volumes` durante a preparação: ele apagaria
os dados dos testes isolados. Não execute `docker compose down` sem os argumentos
explícitos de projeto e arquivo apresentados acima.

Os cenários de publicação, indução de falha e validação de retry/DLQ ficam para
uma etapa posterior. Este ambiente não inclui publicadores ou injeção de falhas.
