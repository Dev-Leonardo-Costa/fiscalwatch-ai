# Validação de prazos para o Beta — publicação 90

Investigação em 11/10/2026, branch feature/validacao-prazos-beta. Consultas somente por GET, sem reanálise, migration ou escrita no banco existente.

## Evidências e causa

A publicação 90 é NT 2026.009 v.1.00, SVRS, publicada em 09/09/2026. O texto armazenado tem 3164 caracteres, hash de texto `140039a189482f88f949690778163c43a9e4a10a8df390126a1c25ce4889ea69`, extrator `svrs-pypdf-v1`, extraído em 09/10/2026. Na página 3, o cronograma da v1.00 contém duas células `Até 17/09/2026`, sob Implantação/Teste e Implantação/Produção. Não é a data de publicação ou um exemplo.

Foi baixado diretamente o PDF oficial (310342 bytes, 5 páginas) e extraído o texto da página 3 com pypdf: ambos os valores e cabeçalhos coincidem com o documento armazenado. Não houve inspeção visual renderizada. URL:
https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico/?sistema=NFE&tipoArquivo=3&nomeArquivo=NT2026.009_v1.00_Alteracao_RV_CFOP.pdf

O Portal NF-e lista a NT 2026.009 v.1.00, publicada em 09/09/2026, em Documentos vigentes:
https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=04BIflQt1aY%3D
Não foi encontrada versão posterior nessa lista consultada. Isso não certifica ausência de qualquer ato posterior nem constitui confirmação persistida de vigência de cada prazo.

A análise 2, automatic-v1, de 09/10/2026, persiste homologação e produção em 17/09/2026. Sua única evidência trata de CFOP I08-140, offsets 1203–1404, sem cronograma ou vínculo aos prazos. A regra atual emite evidências por ambiente e mantém datas passadas apenas nas evidências; portanto o resultado antigo não representa esse comportamento. Não foi confirmado qual revisão do código gerou o registro. O frontend anterior promovia toda data legada a Futuro/Hoje/Vencido sem confirmação.

Caminho: collector document_extraction_service (extração PDF pelo svrs_collector/pypdf) → PublicationDocumentEvent → PublicationService.processEvent → publication_documents → EnvironmentDeadlineRule → RuleBasedImpactAnalyzer → ImpactAnalysisService.toImpactAnalysis/newImpactAnalysis/addEvidenceResults → impact_analyses/evidences → ImpactAnalysisResponse → AnalysisCard → DeadlineSection/deadlineDetails.

## Correção conservadora

Datas legadas recebem Prazo não confirmado e não são exibidas como definitivas nem recebem status temporal. Campos ausentes recebem Não informado; evidências de prazo histórico reconhecidas por ambiente recebem Prazo não confirmado mesmo quando o campo legado é nulo. Homologação e produção permanecem independentes. Evidência documental não equivale a confirmação de vigência. O helper só calcula status com confirmação explícita; nenhum chamador da aplicação a fornece, pois o contrato atual não transporta confirmação auditável. Esta limitação também afeta prazos legítimos: o Beta prefere não afirmar vigência sem suporte persistido.

A extração por cláusula passa a excluir exemplos e simulações, além de publicação, revisão, hipóteses e negações já excluídas. As datas documentais futuras permanecem compatíveis com os campos existentes, mas não são promovidas a definitivas na interface. Não houve mudança de DTO, schema, regras de impacto ou registros.

## Validação manual e plano posterior

1. Após iniciar o frontend atualizado, abrir /publicacoes/90: os dois ambientes devem indicar Prazo não confirmado, sem 17/09/2026 como data definitiva e sem Vencido.
2. Consultar os GETs da publicação e análises: valores legados continuam intactos. Abrir a fonte oficial e conferir página 3.
3. Em publicação sem datas/evidências de prazo, conferir Não informado. Na publicação 94, caso haja evidência histórica tipada por seção, somente o ambiente correspondente indica Prazo não confirmado.

Qualquer correção persistida exige autorização separada: inventariar análises afetadas por GET/consulta read-only, revisar PDF e atos posteriores, registrar a decisão e suas fontes, definir backup e atualização/reanálise seletiva auditável, testar em banco isolado e apresentar os IDs e valores antes/depois para aprovação. Não atualizar automaticamente análises antigas. Um contrato futuro deve transportar confirmação por prazo, versão/etapa, evidência vinculada ao snapshot e data/responsável da verificação; não inferir isso de status REVIEWED, analysisVersion, título ou existência de uma URL.
