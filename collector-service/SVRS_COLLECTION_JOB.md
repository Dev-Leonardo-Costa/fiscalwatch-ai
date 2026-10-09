# Coleta seletiva SVRS

O executor é separado da API: `python -m app.jobs.svrs_collection`. Importar
módulos ou iniciar/recarregar o Uvicorn não inicia tarefas. Está desativado por
padrão e nenhuma configuração dos ambientes existentes foi habilitada.

| Variável | Padrão | Semântica |
|---|---|---|
| `SVRS_COLLECTION_ENABLED` | `false` | Somente `true` habilita execução |
| `SVRS_COLLECTION_INTERVAL_SECONDS` | `300` | Espera após terminar cada ciclo, em segundos |
| `SVRS_COLLECTION_BATCH_SIZE` | `5` | Até este número de tentativas de extração/despacho por ciclo; 1–100 |
| `SVRS_COLLECTION_TIMEOUT_SECONDS` | `30` | Timeout por operação HTTP/RabbitMQ; positivo e finito |
| `FISCAL_SERVICE_BASE_URL` | sem padrão | API Java obrigatória; para integração, `http://127.0.0.1:8081` |

O timeout HTTP é o limite de inatividade de cada operação de rede, não um
prazo total de ciclo nem um limite de CPU para extrair PDF/ZIP. A conexão
RabbitMQ executa um único envio em processo filho com esse prazo, herdando as
variáveis do broker. Se excedido, `subprocess.run` encerra o filho e aguarda seu
término; não sobra uma thread publicando em segundo plano. Essa escolha evita
depender dos timers de `BlockingConnection`, que não disparam na espera
bloqueante por confirms. Há custo de iniciar um processo Python por envio. O ciclo
consulta no máximo dez vezes o lote em identidades e coleta apenas uma página.
Ordena por `external_id` e avança um cursor circular entre ciclos para não
deixar uma falha inicial bloquear indefinidamente os itens seguintes.

## Política de seleção

A identidade permanece SHA-256 de `source:source_identifier.strip()`, sendo
o identificador da SVRS a URL de download. Não há versionamento de URLs cujo
conteúdo muda.

Antes de cada download, o executor consulta
`GET /api/publications/collection-state?externalId=<sha256>`. A consulta retorna
`externalId`, `exists`, `extractionStatus` e `validDocument`, sem texto nem
erro do documento. Diferentemente de `/api/publications/history`, cobre todos
os tipos SVRS e também publicações sem documento. O histórico SCHEMA permanece
inalterado. Erro HTTP, timeout, JSON inválido ou identidade divergente interrompe
o ciclo; nunca significa publicação ausente.

- Ausente: extrair e publicar pelo despacho existente. Uma falha de extração
  pode registrar FAILED para permitir recuperação posterior.
- Existente sem documento, FAILED ou PENDING e com URL: tentar recuperar; só
  publicar quando a nova extração for EXTRACTED, não vazia e sem erro. FAILED
  e PENDING não são considerados processados com sucesso. Extrações ainda
  inválidas não republicam o evento e podem tentar no próximo ciclo.
- EXTRACTED íntegro: ignorar. A integridade é calculada pelo validador Java
  compartilhado com a análise, incluindo hash e tamanho.
- EMPTY, EXTRACTED inválido ou estado desconhecido: ignorar; o `processEvent`
  atual não permite sobrescrever esses estados com segurança. Exigem avaliação
  específica, fora desta automação.
- Sem URL: ignorar até existir documento acessível.

O publisher usa confirms e `mandatory=True`; NACK e return/unroutable propagam
falha. ACK confirma aceitação pelo broker, não commit no PostgreSQL. Após um
envio confirmado, o processo guarda a identidade até observar o estado esperado
no Java; não reenvia enquanto a persistência estiver pendente. Para recuperação,
aguarda um documento válido, não apenas a publicação existente.

NACK/return ou falha na abertura da conexão podem ser tentados em outro ciclo.
Timeout ou falha de resultado ambíguo suspendem a identidade no processo até
que seja observada persistência válida. Investigue broker, DLQ e Java antes de
reiniciar o executor: não há reenvio automático seguro quando o resultado é
desconhecido. A memória de pendências e o cursor são perdidos no reinício.

## Início no Windows (exemplo para uso futuro)

Antes de habilitar, o Java de destino precisa executar esta versão com
`/api/publications/collection-state`. Uma versão antiga responde 404 e o ciclo
para sem despachar. Confirme também os destinos Java e RabbitMQ, as filas e a
topologia correta antes da futura validação real.

Na pasta `collector-service`, configure explicitamente o destino. Obtenha a
senha de desenvolvimento/teste do ambiente seguro; não a grave no repositório.

```powershell
$env:FISCAL_SERVICE_BASE_URL = 'http://127.0.0.1:8081'
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '5673'
$env:RABBITMQ_USERNAME = 'fiscalwatch_it'
$env:RABBITMQ_VIRTUAL_HOST = 'fiscalwatch_it'
$senhaTeste = Read-Host 'Senha do RabbitMQ isolado' -AsSecureString
$env:RABBITMQ_PASSWORD = [System.Net.NetworkCredential]::new('', $senhaTeste).Password
$env:SVRS_COLLECTION_INTERVAL_SECONDS = '300'
$env:SVRS_COLLECTION_BATCH_SIZE = '5'
$env:SVRS_COLLECTION_TIMEOUT_SECONDS = '30'
$env:SVRS_COLLECTION_ENABLED = 'true'
.\.venv\Scripts\python.exe -m app.jobs.svrs_collection
```

Isso inicia execução contínua, com um primeiro ciclo imediato. Ctrl+C encerra.
Para um serviço, use o mesmo comando, diretório de trabalho `collector-service`
e variáveis próprias no supervisor já disponível no sistema, **uma única
instância**, sem `uvicorn --reload`. Nenhum serviço novo é necessário.

Alternativamente, um agendador externo (por exemplo, Agendador de Tarefas do
Windows) pode executar `python -m app.jobs.svrs_collection --once` com as mesmas
variáveis e opção de não iniciar uma segunda instância. Não combine esse modo
com o processo contínuo. O modo externo perde a proteção em memória entre
execuções: antes de adotá-lo, considere o atraso RabbitMQ → PostgreSQL.
Uma execução `--once` com falha retorna código 1 ao supervisor; não repete o
despacho dentro dessa execução. Configure as variáveis no contexto da conta do
serviço/tarefa: variáveis `$env:` definidas em uma sessão interativa não são
automaticamente herdadas por uma tarefa iniciada por outro processo.

Para desabilitar: encerre o executor e configure
`SVRS_COLLECTION_ENABLED=false`. Mudar a variável em outra sessão não modifica
um processo já iniciado. Sem habilitar, o comando apenas informa que está OFF.

## Limitações do MVP

O lock é local ao processo; não coordena instâncias, servidores ou processos
separados. Consultar Java e publicar não é uma operação atômica; outras
instâncias podem enviar o mesmo evento e um reinício pode ocorrer antes do
commit Java. A idempotência Java protege dados existentes, mas não equivale a
exactly-once e a corrida de inserção inicial ainda requer atenção. Use um único
executor e acompanhe DLQ/pendências.

O executor não faz POST de análise. O job documental Java, habilitado
separadamente quando autorizado, encontra documentos após persistência. Para
SCHEMA, este novo caminho não chama a comparação imediatamente após publicar:
essa chamada teria corrida com o listener. Os endpoints manuais e seu fluxo
adicional de schemas mantêm o comportamento anterior; a automação específica
de comparação entre schemas não faz parte desta entrega.

FAILED/PENDING persistidos podem ser tentados uma vez por ciclo, respeitando
lote/intervalo e cursor. Não há contador durável de tentativas de extração;
formatos não suportados precisam de acompanhamento operacional. Não são
consultadas alterações de publicações já EXTRACTED, nem outras fontes.
