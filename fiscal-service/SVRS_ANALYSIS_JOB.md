# Análise documental periódica SVRS

O job está desativado por padrão. Nenhuma variável precisa ser definida para manter esse comportamento.
Quando habilitado, analisa somente documentos SVRS persistidos, EXTRACTED, íntegros e sem análise
documental COMPLETED da versão `automatic-v1`. Análises de schemas não substituem a documental.

## Configuração

| Variável | Padrão | Regra |
|---|---:|---|
| `FISCALWATCH_IMPACT_ANALYSIS_JOB_ENABLED` | `false` | Habilitação explícita |
| `FISCALWATCH_IMPACT_ANALYSIS_JOB_FIXED_DELAY_MS` | `300000` | Pelo menos 1000 ms; contado após terminar o ciclo |
| `FISCALWATCH_IMPACT_ANALYSIS_JOB_INITIAL_DELAY_MS` | `300000` | Zero ou mais; atraso inicial |
| `FISCALWATCH_IMPACT_ANALYSIS_JOB_BATCH_SIZE` | `20` | Entre 1 e 1000 documentos por execução |

As propriedades equivalentes usam o prefixo `fiscalwatch.impact-analysis-job` e os nomes
`enabled`, `fixed-delay-ms`, `initial-delay-ms` e `batch-size`.

Exemplo PowerShell para uma futura execução autorizada, em processo conectado ao ambiente desejado:

```powershell
$env:FISCALWATCH_IMPACT_ANALYSIS_JOB_ENABLED = "true"
$env:FISCALWATCH_IMPACT_ANALYSIS_JOB_FIXED_DELAY_MS = "300000"
$env:FISCALWATCH_IMPACT_ANALYSIS_JOB_INITIAL_DELAY_MS = "300000"
$env:FISCALWATCH_IMPACT_ANALYSIS_JOB_BATCH_SIZE = "20"
```

Definir essas variáveis no host não altera um container já em execução. O job não foi habilitado
nos ambientes existentes durante a implementação.

## Execução e recuperação

Cada ciclo consulta um lote por ID crescente, sem consulta de contagem. O banco pré-filtra estado,
conteúdo e ausência da análise documental. O Java verifica SHA-256 e comprimento Unicode antes
de encaminhar cada candidato ao serviço, que revalida o documento sob lock.

O job suspende transações externas; cada chamada ao serviço transacional realiza seu próprio
commit/rollback. O sucesso só é registrado após o retorno do proxy e seu commit. Uma falha não
interrompe os demais itens. Logs incluem IDs e classe da exceção, sem texto, SQL ou mensagem de erro.

O cursor em memória avança mesmo nas falhas para não bloquear IDs posteriores. Ao atingir um lote
vazio, volta a zero para o próximo ciclo; assim as falhas permanecem elegíveis para novas tentativas.
Não existe retry em loop dentro da mesma execução. Reiniciar perde somente o cursor e retoma as
pendências consultando o banco. Falhas persistentes continuam pendentes e exigem correção; não há
limite global de tentativas entre ciclos nem classificação de falhas nesta etapa.

O controle de sobreposição é local ao bean/processo. Múltiplas instâncias podem selecionar os mesmos
documentos, mas o lock pessimista e a consulta de análise existente em `analyzePublication()` evitam
novas análises duplicadas pelos caminhos coordenados. Pode haver trabalho redundante e espera por
lock; não há eleição distribuída de um executor. Os endpoints REST e as filas não são alterados.

Os testes de integração usam H2 em memória, sem Flyway ou RabbitMQ ativo. Não substituem uma
validação futura de concorrência e desempenho em PostgreSQL isolado.
