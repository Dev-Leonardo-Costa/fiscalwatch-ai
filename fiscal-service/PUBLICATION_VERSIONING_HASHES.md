# Identidade e hashes — contrato experimental v1

Este contrato pertence ao componente puro `PublicationSnapshotHasher`. Não é
chamado por `PublicationService.processEvent()`, não altera eventos, tabelas ou
regras de recuperação. Sua ativação futura exige aprovação e testes de migração.

## Identidade

`external_id` continua sendo a identidade existente, sem recálculo no Java.
Python `publication_identity_service.generate_external_id()` usa SHA-256 de
`source + ':' + source_identifier.strip()`. Não é hash de conteúdo. SVRS/NF-e/
CGIBS usam URL; Receita usa URL canônica; DOU usa `urlTitle`. Mudança de URL pode
gerar outra identidade: aliases e unificação de famílias ficam fora desta etapa.

## Três hashes diferentes

- **Conteúdo extraído:** SHA-256 dos bytes UTF-8 exatos de `content_text`, em
  hexadecimal minúsculo. PDF usa texto normalizado pelo extrator; HTML usa
  conteúdo selecionado; SVRS ZIP agrega arquivos ordenados, caminhos e conteúdo.
  Os extratores Python não possuem uma canonicalização universal compartilhada.
- **Snapshot:** SHA-256 dos bytes UTF-8 da representação v1 abaixo. Inclui
  metadados e hash do conteúdo; não representa apenas o texto.
- **Bytes originais:** SHA-256 do arquivo baixado. Não é calculado por este
  componente nem deve ser inferido a partir de `content_hash`. Mudanças visuais,
  assinatura, compactação ou conteúdo descartado podem não mudar o texto extraído.

Java não reaplica normalização Python nem normaliza NFC/NFD, espaços, caixa,
URLs ou CRLF. Duas strings Unicode visualmente equivalentes podem ter hashes
diferentes. Isso preserva o contrato atual. Surrogates isolados são rejeitados,
assim como o encoding UTF-8 estrito do Python, evitando substituição por `?`.

## Representação canônica do snapshot

Prefixo literal `fiscalwatch.publication-snapshot:v1\n`, seguido das chaves em
ordem lexicográfica, independentemente da ordem de campos no JSON recebido:

1. `description`
2. `document_content_hash`
3. `document_source_url`
4. `document_type`
5. `download_url`
6. `external_id`
7. `modified_at`
8. `published_at`
9. `source`
10. `title`

Cada campo usa `chave:comprimento_em_bytes_utf8:valor\n`. Nulo usa
`chave:-1:\n`; vazio usa `chave:0:\n`. O framing por comprimento distingue
delimitadores e quebras de linha dentro dos valores. Não existe mapa arbitrário
como entrada: os campos são projetados dos records existentes.

Datas locais usam `uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS`, Locale.ROOT, sempre com
nove dígitos fracionários. Nenhum fuso é presumido; os contratos atuais usam
LocalDateTime/datetime. Precisão e timezone entre evento e banco precisam ser
resolvidos antes de recomputar hashes a partir de registros PostgreSQL.

Excluídos: `occurred_at`, `extracted_at`, `extractor_version`, `content_length`,
erros, headers e horários de persistência. Estado de extração valida a
elegibilidade, mas não faz parte do hash. Alteração de título, descrição, URL,
tipo ou datas oficiais gera outro snapshot mesmo com conteúdo igual.

## Regras de elegibilidade

| Entrada | Resultado |
| --- | --- |
| Sem documento | Snapshot apenas de metadados, contentHash nulo |
| EXTRACTED com texto não nulo/não blank e sem erro | Candidato válido |
| Hash ausente (`null`) em candidato válido | Calcular SHA-256 do texto |
| Hash declarado com 64 caracteres hex e igual ao calculado | Aceitar; retornar minúsculo |
| Hash vazio, malformado ou inconsistente | IllegalArgumentException; sem fallback silencioso |
| FAILED, PENDING, EMPTY ou estado ausente | IllegalArgumentException; não criar hash de versão válida |
| EXTRACTED vazio/blank ou com erro | IllegalArgumentException |

`calculateContentHash()` é uma operação de baixo nível e pode calcular o hash
de string vazia; `calculate()` recusa texto vazio como documento EXTRACTED.
Não converter FAILED/PENDING em documento ausente para contornar a validação.
`content_length` não é validado nem usado na identidade nesta etapa; no Python
é comprimento em caracteres, não necessariamente bytes nem unidades UTF-16.

## Próxima etapa

As exceções deste componente não estão ligadas ao listener. Antes da integração,
decidir encaminhamento de inconsistências, snapshots de metadados incompletos,
ordenação de eventos, hashes históricos divergentes e evolução do formato v1.
Não corrigir hashes antigos automaticamente. Preservar a recuperação existente
FAILED/PENDING e nunca substituir conteúdo válido por falha de extração.
Hash identifica igualdade de dados, não autenticidade da fonte ou ordem temporal.
