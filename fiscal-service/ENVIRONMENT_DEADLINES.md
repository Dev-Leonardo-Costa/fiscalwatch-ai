# Extração conservadora de prazos fiscais

`EnvironmentDeadlineRule` usa somente o documento com extração `EXTRACTED`.
Não concatena título, descrição e conteúdo; título e capa servem apenas para
identificar a versão declarada. Datas de publicação não são implantação.

## Critérios e compatibilidade

- O cronograma precisa de cabeçalhos ordenados Teste / Produção e linhas de
  versão reconhecíveis. A versão declarada seleciona a linha; na ausência dela,
  só uma versão única pode ser selecionada. Não se escolhe a maior versão nem
  a data mais recente.
- Cada linha precisa de duas células explícitas: data, `-x-` ou `Sem alteração`.
  Marcadores não viram datas nem herdam valores anteriores. Uma célula ausente,
  versões conflitantes, múltiplas tabelas ou várias etapas/grupos exigem revisão.
- Fora de tabelas, exige associação direta ambiente/data, na mesma cláusula,
  sem negação, hipótese ou contexto de publicação/revisão. Não há busca por
  proximidade. Textos com histórico, sumário ou cronograma não reconhecido não
  recebem esse fallback.
- O parser de calendário é estrito. Datas passadas, em relação ao relógio de
  análise no fuso America/Fortaleza, ficam apenas nas evidências, com
  `histórico, vigência não confirmada`. Isso não afirma que foram revogadas.
  Os campos legados ficam nulos para evitar apresentação como prazo vigente.
- Datas de hoje/futuras são datas documentais, não confirmação de vigência
  jurídica nem de ausência de alterações posteriores. A evidência explicita
  `vigência externa não verificada`. Esse controle externo ainda não existe.
- Evidências preservam URL, trecho literal e offsets no documento. A referência
  de seção informa versão, etapa (quando única) e ambiente. Página fica nula:
  a extração atual não fornece um mapa de páginas confiável.
- O analisador agrega evidências de prazos sem eliminar o fallback de revisão
  técnica e sem mudar níveis/prioridades das regras de impacto.

DTOs, entidades, mapper e `ImpactAnalysisService.addEvidenceResults` já
transportam esses campos. Nenhum contrato, schema ou migration foi alterado.
`analysisVersion` é a versão do analisador, não a versão da Nota Técnica.

## Regressão da NT 2021.003 v1.21

O fixture `src/test/resources/deadlines/nt2021-003-v1-21-extracted-prefix.txt`
contém os primeiros 6500 caracteres do texto local da publicação 94, consultado
por GET em 10/10/2026. O documento completo tem 31256 caracteres e hash
`ea087042dca8feb748f4f5381e1c0f7a8f2a2b93056facb2a1ea4ec15e36bd2e`.
Origem registrada:
https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico/?sistema=NFE&tipoArquivo=3&nomeArquivo=NT2021.003_v1_21+-+Valida%C3%A7%C3%A3o+GTIN.pdf

A linha v1.21 termina em `-x- 03/07/2023`: homologação sem data explícita;
produção documental em 03/07/2023, histórica. A regra produz uma evidência
específica dessa linha e mantém ambos os campos legados nulos. Não herda
04/07/2022, 12/09/2022 ou datas da v1.20. A análise 5 persistida não foi alterada.

Não houve confirmação visual no PDF original nem consulta a alterações
oficiais posteriores. O fixture reproduz o texto armazenado, não certifica
fidelidade da extração ou vigência atual. O comportamento é conservador e pode
deixar de identificar prazos legítimos em formatos não reconhecidos.

## Parte que exige modelagem posterior (não implementada)

A estrutura atual permite somente uma data por ambiente. Versão/etapa/ambiente
ficam em texto na evidência, sem relação tipada com um prazo; não permite
representar vários grupos nem distinguir formalmente data histórica, vigente,
revogada, não identificada e não confirmada.

1. Confirmar visualmente células do PDF e consultar publicações oficiais
   posteriores, com registro de fonte, data e responsável pela verificação.
2. Propor snapshots/versionamento documental e uma coleção de prazos com
   versão, etapa/grupo, ambiente, marcador/status, data e vínculo à evidência
   e ao hash do documento. Schema novo exigiria migration e revisão dos DTOs;
   apresentar esse contrato e o impacto antes de implementar.
3. Manter os campos legados como projeção apenas quando a seleção for única
   e a política de vigência for satisfeita. Testar adiamentos, substituições,
   versões conflitantes, múltiplos grupos e evidência por célula.
4. Planejar separadamente reanálise controlada e atualização da interface para
   distinguir data documental e vigência verificada. Nenhum job, reanálise ou
   alteração de registros faz parte desta correção.
