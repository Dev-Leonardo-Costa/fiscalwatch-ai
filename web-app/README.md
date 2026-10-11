# FiscalWatch AI — interface inicial

Next.js App Router, TypeScript e Tailwind CSS. Consultas executadas no servidor
Next.js. Nenhum job, POST ou serviço backend é iniciado pela interface.

## Executar no Windows

Requer Node.js >=20.9.0 (validado com 20.18.3).

```powershell
cd web-app
npm ci
Copy-Item .env.example .env.local
# Confira FISCAL_SERVICE_BASE_URL em .env.local antes de iniciar.
npm run dev
```

Abra http://127.0.0.1:3000 e pesquise por número da NT, ano, título ou palavra-chave
(por exemplo, `2021.003`, `2023` ou `GTIN`). A pesquisa fica na URL `/?q=GTIN`.
Para o ambiente
isolado existente, http://127.0.0.1:3000/publicacoes/94 consulta a publicação 94.
A URL Java padrão do exemplo é http://127.0.0.1:8081. Não há fallback silencioso
para o serviço principal. A variável não possui prefixo NEXT_PUBLIC_ e o
módulo fiscal-service é protegido por `server-only`.

```powershell
npm run lint
npm run typecheck
npm test
npm run build
npm start
```

Os testes automatizados usam fetch simulado, sem acessar bancos ou brokers.
O build não consulta a API; a rota de detalhes é dinâmica e usa `no-store`.

## Contratos e organização

- `src/services/http.ts`: timeout de 10 segundos, GET, erros seguros e validação
  das respostas com Zod; não exibe mensagens internas retornadas pelo backend.
- `src/services/fiscal-service.ts`: somente
  `GET /api/publications?q={termo}`,
  `GET /api/publications/{id}` e
  `GET /api/impact-analyses/publications/{publicationId}`.
- `src/types/fiscal.ts`: schemas e tipos baseados nos records PublicationResponse,
  ImpactAnalysisResponse, TechnicalImpactResponse, ActionItemResponse e EvidenceResponse.
- `src/lib/formatters.ts`: datas LocalDateTime sem presumir UTC, rótulos e links
  externos restritos a HTTP/HTTPS sem credenciais.
- `src/app/publicacoes/[id]`: metadados e análises; preserva a publicação na tela
  se apenas a consulta de análises falhar. Um array vazio significa ausência de
  análise registrada, não uma falha do serviço.

Datas sem fuso são apresentadas com o horário recebido. Os conteúdos externos
são texto escapado pelo React; nenhum HTML remoto é renderizado. Impacto e
estado são apresentados com texto, além da cor. Ações são apenas leitura.
`automatic-v1` identifica análise documental; `schema-comparison-v2` identifica
comparação de schemas, conforme a constante Java. Outras versões são exibidas
sem classificação presumida.

## Limitações

### Prazos de implantação

Cada análise apresenta homologação e produção em um bloco destacado, separado
do nível de impacto técnico. Somente `homologationDeadline` e
`productionDeadline` alimentam esse bloco. Valores nulos ou inválidos exibem
“Prazo não identificado”, inclusive quando não há análises ou a consulta falha.
Não há fallback para publicação, modificação, prioridade ou prazo de ação.
Datas válidas são comparadas por dia em `America/Fortaleza`: futuro, vencido ou
hoje. O horário não é tratado como limite, pois a extração Java usa meia-noite
para datas documentais sem horário; LocalDateTime não contém fuso.

Inspeção do backend: os campos já existem na entidade ImpactAnalysis, nos DTOs
de entrada/saída e na migração V3. ImpactAnalysisService persiste os resultados,
ImpactAnalysisMapper os expõe e o GET de análises por publicação os retorna.
PublicationEntity/PublicationResponse possuem datas de publicação e modificação,
que não são datas de implantação. EnvironmentDeadlineRule extrai datas
`dd/MM/yyyy` próximas de termos de ambiente ou em cronogramas estruturados,
a partir do contexto textual (título, descrição e conteúdo extraído).
SchemaChangeImpactAnalyzer retorna ambos os prazos nulos.

Verificação local em 09/10/2026: publicação 94, análise 5 (`automatic-v1`),
retornou ambos os prazos `2022-07-04T00:00:00`, enquanto a publicação é de
30/05/2023. São valores efetivamente retornados pela API, mas a inspeção não
comprova que sejam prazos vigentes dessa versão. A interface alerta sobre
cronogramas históricos e permite consultar o documento oficial.

Trabalho posterior no backend: associar cada data à versão e ao ambiente
corretos, validar datas estritamente, resolver cronogramas com múltiplas versões
e distinguir datas históricas de implantação vigente. Expor por prazo a
evidência (trecho, documento/página), versão e estado de validação/confiança;
hoje esses metadados não existem no contrato. Para schemas, vincular uma fonte
documental de cronograma antes de preencher datas. Campos nulos, por si só,
não distinguem ausência no documento de extração ainda não realizada.

Pesquisa: `GET /api/publications?q=GTIN` retorna um array de PublicationResponse,
com até 50 publicações ordenadas por publishedAt e ID decrescentes. O termo
deve ter de 1 a 200 caracteres e é procurado literalmente, sem distinção de
maiúsculas/minúsculas, no título, descrição ou ano de publicação. O ano de uma
NT também pode ser encontrado quando consta no título/descrição. Acentos e
pontuação devem corresponder ao texto; não há busca no corpo do documento.
Termos vazios/ausentes/longos retornam 400; ausência de resultados retorna [].
A interface inclui carregamento, ausência de resultados, erro seguro e acesso
aos detalhes. Reenvie a pesquisa para tentar novamente após uma falha.
Não há paginação nesta etapa: refine o termo quando atingir 50 resultados.

Não há
dashboard, autenticação, pagamentos, cadastro ou atualização de ações. A
interface não inicia análises nem coleta publicações. A evidência pode não
ter página, seção ou posição; isso é mostrado sem inventar dados. Recomendações
precisam de revisão técnica, especialmente quando os documentos são históricos.

A resposta 404 da API gera a tela de publicação não encontrada. Como a rota
possui carregamento com streaming, o Next.js pode já ter enviado HTTP 200 antes
de renderizar essa tela; o status HTTP da página não substitui o status da API.
