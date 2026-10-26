# Histórico documental de NT — etapa 1

## Persistência e compatibilidade

A migration V8 é aditiva. V1–V7 permanecem intactas. Não existe backfill de
snapshots, revisões ou vínculos de análises legadas. `publications.id` e
`external_id` continuam sendo as identidades das ocorrências nas fontes.

- `technical_notes`: identidade fiscal `(family, note_year, note_number)`.
- `technical_note_revisions`: `(NT, declared_version, content_hash, source)`.
  Mudança de texto sob a mesma versão cria outra revisão. Fontes diferentes
  continuam separadas, inclusive quando o texto é igual.
- `publication_document_snapshots`: insumos imutáveis já definidos pela V6.
  Sua identidade inclui texto, URL, versão declarada e extrator; não é a mesma
  identidade de uma revisão fiscal. URLs/extratores diferentes podem produzir
  snapshots diferentes da mesma revisão de texto na mesma fonte.
- `publication_snapshot_receipts`: metadados recebidos com cada snapshot,
  proveniência e vínculo opcional à revisão. Não sobrescreve metadados legados.
- `impact_analyses.document_snapshot_id`: insumo da nova análise documental.
  Chave única `(snapshot, analysis_version)`, com NULL permitido para o legado,
  análises externas/manuais e comparações de schema que não usam este fluxo.

Somente documentos EXTRACTED que passam pela validação de hash e comprimento
entram nos snapshots. O tratamento legado de extrações inválidas permanece;
elas não se tornam insumos válidos por estarem armazenadas.

A ingestão anexa snapshots antes do retorno que preserva `publication_documents`.
Portanto o documento legado pode continuar em 1.51 enquanto os snapshots contêm
1.51 e 1.52 da mesma URL. Não se deve usar a tabela legada como visão da versão
mais recente. A mesma transação persiste snapshot, revisão e metadados.

Locks da publicação serializam ingestão e análise. Corridas na criação inicial
da publicação/identidade/revisão são resolvidas por constraints e repetição da
transação inteira após rollback, limitada a três tentativas. Quando o chamador
já possui uma transação, a falha é propagada; ele deve refazer sua transação.

## Identificação conservadora

Nesta etapa a família canônica suportada é `NFE_NFCE`, em documentos
NOTA_TECNICA de PORTAL_NFE ou SVRS. Na SVRS exige-se indicação de NF-e/NFC-e no
título ou caminho `/NFE/`; a fonte sozinha não estabelece a família. CT-e/MDF-e
não são atribuídos automaticamente a NF-e. Número/ano e versão são extraídos
do título/capa. A versão da capa prevalece sobre título antigo de URL reutilizada.
Ambiguidades e conflitos de número não geram identidade fiscal presumida:
o snapshot e sua proveniência são preservados sem revisão canônica.

Nenhuma fonte substitui o conteúdo da outra. Política de precedência oficial,
reconciliação de divergências e identificação de outras famílias ficam para
etapas posteriores.

## API

- `GET /api/publications/{id}/snapshots`: lista os snapshots em ordem de captura,
  com IDs, versão, revisão, identidade fiscal, fonte, hash e texto.
- `POST /api/impact-analyses/publications/{publicationId}/snapshots/{snapshotId}/analyze`:
  analisa exatamente esse snapshot, verificando sua publicação.
- `POST /api/impact-analyses/publications/{publicationId}/analyze`: usa o snapshot
  com maior ID da publicação. É o último snapshot novo recebido, não uma
  determinação de qual versão fiscal está vigente. Reentrega de snapshot já
  conhecido não altera essa seleção. Use o endpoint explícito para versões antigas
  ou recebidas fora de ordem.
- Respostas de análise incluem `documentSnapshotId`, nullable para o legado.

Sem snapshots, uma análise automática concluída existente continua sendo
retornada sem alteração ou associação retroativa. Se não houver análise prévia,
o documento legado válido pode ser capturado como insumo da análise criada agora.

Nenhum job foi habilitado ou criado. O job SVRS existente passa a verificar
conclusão em relação ao último snapshot, mantendo sua configuração e agendamento.
Ele não percorre todas as versões intermediárias; estas permanecem analisáveis
pelo endpoint explícito. O coletor periódico continua sem revisitar documentos
que considera válidos; a captura de mudanças na mesma URL depende do recebimento
de outro evento. Não houve dispatch real nem ampliação de coleta nesta etapa.

## Integridade e limites

V8 bloqueia UPDATE/DELETE das identidades, revisões e recibos, reescrita de
análises vinculadas e de seus impactos/evidências/ações. Também bloqueia TRUNCATE
dessas estruturas. V6 protege os snapshots. Uma FK impede remoção de snapshots
referenciados e o trigger verifica que a análise pertence à publicação do snapshot.
As proteções não substituem controle de acesso administrativo ao PostgreSQL.

O conteúdo preservado é o texto extraído. O contrato atual do coletor não envia
hash/binário original do PDF; `original_pdf_hash` continua NULL. Uma diferença
apenas nos bytes do PDF, sem alteração no texto, não caracteriza revisão de texto.
Não há comparação documental, confirmação de vigência ou prazos por regra nesta etapa.

## Testes isolados

`TechnicalNoteHistoryIntegrationTest` usa exclusivamente H2 em memória, Flyway
desabilitado, listeners e job desabilitados. Cobre 1.51/1.52, URL reutilizada,
revisões, proveniência, concorrência, falhas transacionais, legado e idempotência.

`TechnicalNoteHistoryPostgresTest` repete esses cenários com V1–V8 e testa os
triggers. `TechnicalNoteHistoryMigrationPostgresTest` cria um schema exclusivo,
aplica V1–V7, grava fixtures legadas e aplica V8 verificando preservação integral.
Ambos são opt-in: `-Dfiscalwatch.history-postgres-test=true`, e usam exclusivamente
`127.0.0.1:55449/fiscalwatch_history_test`, usuário `history_test`. Prepare antes um
cluster temporário independente: nunca aponte esses testes para um banco existente.
Sem o opt-in eles são ignorados e não abrem conexão PostgreSQL.

Validação desta implementação: 312 testes passaram, sem falhas ou testes ignorados,
com JDK 21, H2 em memória e PostgreSQL 14 temporário exclusivo. Inclui aplicação
limpa de V1–V8, upgrade V7–V8 com fixtures legadas e proteções SQL. Nenhum banco
existente foi usado. Relatório local: `target/history-final-tests.log`.

## Inventário de arquivos

Caminhos relativos a `fiscal-service/`. Nenhum arquivo do frontend, coletor,
configuração de produção ou migrations V1–V7 foi modificado.

Novos:

```text
TECHNICAL_NOTE_HISTORY.md
src/main/resources/db/migration/V8__technical_note_version_history.sql
src/main/java/br/com/fiscalwatch/fiscalservice/publication/dto/PublicationSnapshotResponse.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/entity/TechnicalNoteEntity.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/entity/TechnicalNoteRevisionEntity.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/entity/PublicationSnapshotReceiptEntity.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/repository/TechnicalNoteRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/repository/TechnicalNoteRevisionRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/repository/PublicationSnapshotReceiptRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/service/PublicationSnapshotService.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/versioning/TechnicalNoteIdentity.java
src/test/java/br/com/fiscalwatch/fiscalservice/TechnicalNoteHistoryIntegrationTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/TechnicalNoteHistoryPostgresTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/TechnicalNoteHistoryMigrationPostgresTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/TechnicalNoteIdentityTest.java
```

Alterados:

```text
src/main/java/br/com/fiscalwatch/fiscalservice/publication/entity/PublicationEntity.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/repository/PublicationDocumentRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/repository/PublicationDocumentSnapshotRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/service/PublicationService.java
src/main/java/br/com/fiscalwatch/fiscalservice/publication/controller/PublicationController.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/entity/ImpactAnalysis.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/repository/ImpactAnalysisRepository.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/service/ImpactAnalysisService.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/mapper/ImpactAnalysisMapper.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/dto/ImpactAnalysisResponse.java
src/main/java/br/com/fiscalwatch/fiscalservice/impactanalysis/controller/ImpactAnalysisController.java
src/test/java/br/com/fiscalwatch/fiscalservice/ImpactAnalysisServiceTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/ImpactAnalysisIdempotencyIntegrationTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/PublicationCollectionStateTest.java
src/test/java/br/com/fiscalwatch/fiscalservice/PublicationSearchTest.java
```
