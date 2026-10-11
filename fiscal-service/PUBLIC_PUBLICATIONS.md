# Publicações públicas e fixtures internas

A migration V7 adiciona `publications.record_origin`, com valores `PUBLIC` (padrão compatível) e `INTERNAL_TEST`. `PUBLIC` significa elegível para consulta pública, não confirmação de autenticidade documental.

O diagnóstico por GET, sem escrita, encontrou fixtures com `source=INTEGRATION_TEST`, identificadores SVRS `it-svrs-job-<32 hex>-valid/failed` e URLs SVRS `https://fixtures.invalid/svrs/mvp-<32 hex>.pdf`. A V7 classifica somente esses namespaces exatos; não usa título, descrição, ano, IDs numéricos nem hashes genéricos. Os scripts que produziram os registros locais não foram encontrados no repositório: sua autoria não está confirmada. A entrada atual permite criação por POST ou eventos, ambos sem classificação explícita antes desta alteração.

A coluna é necessária porque fixtures também usam `source=SVRS`, assim como documentos legítimos. A migration é aditiva: preserva todos os registros, documentos, análises e snapshots. Atualiza apenas a nova classificação. Um trigger aplica os mesmos marcadores aos futuros INSERTs/UPDATEs por SQL; callbacks JPA fazem o mesmo na ingestão atual. Fixtures fora desses namespaces devem declarar `recordOrigin: "INTERNAL_TEST"` no pedido de criação. Pedidos antigos continuam aceitos, e respostas existentes não mudam de formato.

Busca, detalhes de publicação, histórico e consultas de análises retornam apenas `PUBLIC`. Detalhes e análises de registros internos retornam 404; collection-state apresenta registro interno como inexistente. Repositories internos e fluxos de criação/análise permanecem disponíveis. Nenhum registro é excluído. O frontend recebe os resultados já filtrados, sem heurísticas próprias.

## Validação

Os testes de busca usam H2 isolado e cobrem ano, título, descrição, fixtures explícitas, marcadores conhecidos e documentos públicos cujo título contém TESTE/sintética. Também verificam detalhes, histórico e análises. Os scripts SQL em `src/test/resources/publication-origin` permitem validar V1–V7 em PostgreSQL descartável: aplicar V1–V6, seed.sql, V7 e assertions.sql, com `ON_ERROR_STOP=1`. Nunca usar esses scripts em um banco existente.

Após revisar e aplicar V7 pelo procedimento normal de implantação e reiniciar o backend, consultar `/api/publications?q=2026`, `?q=GTIN` e `?q=2026.009`. No banco local investigado, publicações 1, 2, 89, 91 e 92 devem desaparecer das consultas públicas; 90 e 94 continuam acessíveis. Detalhes e análises das fixtures devem retornar 404. No navegador, repetir as buscas pela interface e abrir um resultado legítimo. A migration não foi executada contra esse banco durante o desenvolvimento.

## Limitações

Fixtures antigas sem marcador inequívoco não podem ser separadas de documentos legítimos automaticamente. Não se deve classificá-las pelo título: é necessária revisão com evidência de origem e classificação explícita em uma etapa autorizada. Testes persistentes futuros devem usar origem interna e banco isolado. Há testes legados que usam o datasource configurado; eles não devem ser executados indiscriminadamente contra bancos com dados do produto. Esta alteração não implementa autenticação nem restrições novas para endpoints de escrita.
