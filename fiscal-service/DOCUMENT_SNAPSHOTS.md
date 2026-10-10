# Base aditiva de snapshots documentais

V6 cria `publication_document_snapshots`, sem alterar V1–V5, entidades ou
fluxos legados. Nenhuma coleta, análise, job, backfill ou endpoint grava
snapshots automaticamente nesta etapa.

Cada snapshot pertence a uma publicação e preserva texto exato, SHA-256 do
texto em UTF-8 (`text_hash`), comprimento em code points, URL, versão declarada,
extrator e horários de extração/captura disponíveis. Metadados desconhecidos
ficam nulos. `created_at` é o instante de persistência em UTC, não de captura.
Datas herdadas do contrato atual usam LocalDateTime; não se presume um fuso
para extracted_at/captured_at recebidos.

`original_pdf_hash` é separado e opcional: somente deve receber um SHA-256
medido sobre bytes do PDF por um chamador com acesso ao artefato original.
A base valida o formato, mas não pode comprovar bytes que não recebeu.
Nunca calcula esse campo a partir do texto nem copia `text_hash` para ele.

## Identidade e imutabilidade

Identidade única: `(publication_id, snapshot_hash)`. O hash usa o namespace
`fiscalwatch.document-snapshot:v1`, campos prefixados por comprimento UTF-8 e
inclui text_hash, URL, versão declarada, versão do extrator e hash binário
quando informado. É distinto do contrato experimental de
`PublicationSnapshotHasher.calculate`, que agrega metadados da publicação.
Reutiliza somente `calculateContentHash`, sem alterar o contrato experimental.

Horários de captura/extração não participam da identidade: retries idênticos
retornam o primeiro snapshot e preservam seus metadados. Uma mudança de texto,
URL, versão, extrator ou hash binário cria outra identidade. Assim, conteúdos
diferentes na mesma URL/versão declarada não são sobrescritos. Repetidas
observações/capturas do mesmo conteúdo não são modeladas individualmente;
um histórico de tentativas exigirá outra coleção posteriormente.

O repository oferece consultas e `append`, sem save/merge/delete. `append`
serializa escritores por lock da publicação, em READ_COMMITTED, consulta a
identidade e usa persist somente para registros novos. A constraint única
protege acessos que não utilizam esse protocolo. Chamadas dentro de uma
transação com isolamento diferente devem respeitar a visibilidade do lock.

Entidade sem setters, colunas não atualizáveis, Hibernate @Immutable e callback
de remoção protegem o uso JPA. V6 acrescenta trigger PostgreSQL que rejeita
UPDATE/DELETE nativos ou em lote e TRUNCATE; a FK da publicação usa ON DELETE RESTRICT.
Isso bloqueia a exclusão de uma publicação que tenha snapshots, sem mudar o
comportamento de publicações que ainda não tenham nenhum. Privilégios de dono/
administrador capazes de desabilitar triggers não são tratados como acesso
imutável. A aplicação deve usar privilégios mínimos.

Validação Java verifica conteúdo, hash, comprimento, identidade e limites.
Checks PostgreSQL verificam formato dos hashes, texto não vazio, comprimento
e SHA-256 UTF-8 do texto, sem depender de pgcrypto. Não há extração automática
da versão declarada; o chamador deve fornecê-la somente quando identificada.

## Verificação e limites

Os testes de repository usam H2 em memória, Flyway desabilitado e schema JPA
isolado. Verificam persistência, Unicode, duplicidade, idempotência, metadata,
proteção JPA e rejeição de integridade inválida. Não conectam ao PostgreSQL
existente nem executam jobs.

A sequência SQL V1–V6 foi aplicada com sucesso em PostgreSQL 17 descartável,
criado com imagem Docker local, sem rede, portas ou volumes compartilhados.
O script `src/test/resources/snapshots/postgresql_snapshot_assertions.sql`
validou Unicode, conteúdos distintos na mesma URL/versão, duplicidade,
hash/comprimento incorretos, formato do hash binário, FK e rejeição de
UPDATE/DELETE/TRUNCATE. Seus dados são revertidos por rollback; o container e
seu armazenamento anônimo são removidos após a validação.

Ainda faltam teste concorrente do append e validação JPA/Flyway integrada no
PostgreSQL descartável (a execução SQL acima usa psql). H2 não prova essas
garantias específicas de produção. Nenhuma migration foi executada contra
bancos existentes. A implantação nesses bancos precisa de autorização própria.

Futuras evidências poderão referenciar o ID do snapshot, seu text_hash e
offsets sobre content_text exato. Prazos poderão referenciar essas evidências,
preservando separadamente versão do PDF e versão da linha do cronograma.
Esta etapa não cria evidências de snapshot, prazos, vigência, relação entre
famílias documentais, armazenamento binário ou endpoint de consulta.
