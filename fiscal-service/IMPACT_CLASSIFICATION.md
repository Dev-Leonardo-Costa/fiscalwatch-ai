# Classificação de impacto documental

Em `automatic-v1`, HIGH não é mais atribuído pela ocorrência isolada de
`schema`, `layout` ou `leiaute`. O caminho anterior de `resolveImpactLevel()`
avaliava título, descrição e conteúdo juntos; a primeira ocorrência podia
estar no sumário. `withImpactLevel()` e `withPriority()` copiavam o nível
global para todos os resultados, promovendo ou rebaixando níveis específicos.

Agora `StructuralChangeRule` exige uma declaração explícita de alteração ou
modificação cujo objeto seja leiaute, layout ou schema, ou uma declaração
passiva equivalente. Avalia documento extraído, título e descrição
separadamente, sem formar frases pela concatenação de campos. Descarta
entradas de índice com pontilhado ou em bloco identificado como sumário/índice,
perguntas, exemplos/hipóteses ou referências informativas explícitas e declarações
negadas. Registra o trecho da declaração; offsets
documentais referem-se ao conteúdo original. Evidências de metadados não
recebem offsets de documento.

`NegationDetector` considera a declaração antes e depois do termo, delimitada
por pontuação, parágrafos e conectivos de contraste. Não depende de uma janela
fixa de 80 caracteres nem transfere a negação entre declarações separadas.
Normaliza acentos depois de delimitar o trecho no texto original.

O nível global é o máximo dos níveis de impactos técnicos produzidos pelas
regras, na ordem `NONE < LOW < MEDIUM < HIGH < CRITICAL`. Níveis específicos e
prioridades das ações são preservados. A prioridade é uma decisão da regra,
não uma cópia obrigatória do máximo global. Uma mudança estrutural HIGH pode
coexistir com um impacto de CFOP MEDIUM e ações de outra prioridade.

Sem impacto específico reconhecido, mantém-se o fallback conservador existente:
MEDIUM para menções a prazo/homologação, LOW nos demais casos. O resumo e a
descrição explicitam que esse fallback exige revisão manual e não comprova
baixo impacto ou ausência de mudança. O contrato atual não possui um nível
`UNKNOWN` nem um campo de confiança. Não é possível concluir o nível técnico
correto da NT 2021.003 somente pela ausência de evidência de mudança estrutural.

O caminho `schema-comparison-v2`, em `SchemaChangeImpactAnalyzer`, classifica
diferenças XSD estruturadas (tipos, cardinalidade, elementos e facetas), não
palavras do documento. Seu comportamento permanece preservado, assim como
as regras de CFOP/I08-140 e a extração de prazos.

## Limitações

Esta detecção é determinística e conservadora, não uma análise linguística
completa. Formulações não reconhecidas ou ambiguidades na mesma declaração
podem resultar em fallback. A extração linear não oferece estrutura completa
de tabelas e seções; a regra não resolve versões históricas, vigência ou todas
as formas de índice. Uma afirmação explícita em título/descrição pode sustentar
o critério, mas a fonte oficial e a aplicabilidade ao ERP precisam ser revisadas.
HIGH continua sendo uma heurística de mudança estrutural, não uma estimativa
quantitativa de esforço, urgência ou prazo.

Nenhum registro é atualizado automaticamente por essa correção. Análises
`COMPLETED` existentes podem ser reutilizadas pelo serviço; sua atualização
depende de uma futura reanálise controlada. A versão `automatic-v1` foi mantida,
sem disparar jobs ou alterar a política de idempotência nesta etapa.
