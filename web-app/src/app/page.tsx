import { PublicationSearch } from "@/components/publication-search";
import { searchPublications } from "@/services/fiscal-service";
import { formatLocalDateTime } from "@/lib/formatters";
import { ApiError } from "@/services/http";
import Link from "next/link";
import type { Publication } from "@/types/fiscal";

export default async function Home({ searchParams }: { searchParams: Promise<{ q?: string | string[] }> }) {
  const params = await searchParams;
  const query = typeof params.q === "string" ? params.q.trim() : "";
  let results: Publication[] = [];
  let error = "";
  if (query) {
    if (query.length > 200) error = "Informe uma pesquisa de até 200 caracteres.";
    else try { results = await searchPublications(query); }
    catch (cause) { error = cause instanceof ApiError ? cause.message : "Não foi possível pesquisar. Tente novamente."; }
  }
  return <div className="home">
    <div className="eyebrow">INTELIGÊNCIA FISCAL PARA EQUIPES DE TECNOLOGIA</div>
    <h1>Da publicação oficial<br />à decisão técnica.</h1>
    <p className="lead">Consulte documentos fiscais e entenda os impactos, as ações recomendadas e as evidências que apoiam a evolução do seu ERP.</p>
    <section className="panel search-panel" id="consultar"><div className="section-heading"><div><span className="eyebrow">CONSULTA</span><h2>Pesquise publicações fiscais</h2></div><span className="panel-icon" aria-hidden="true">↗</span></div><PublicationSearch key={query} query={query} />
      {query && <div className="search-results" aria-label="Resultados da pesquisa">
        {error ? <p className="form-error" role="alert">{error}</p> : results.length === 0
          ? <p className="muted" role="status">Nenhuma publicação encontrada para “{query}”. Tente outro termo.</p>
          : <><p className="muted text-sm" role="status">{results.length} publicação(ões) encontrada(s) para “{query}”.</p>
            <ul>{results.map(publication => <li key={publication.id}>
              <h3><Link className="text-link" href={`/publicacoes/${publication.id}`}>{publication.title ?? "Publicação fiscal"}</Link></h3>
              <p className="muted text-sm">{publication.source ?? "Fonte não informada"} · Publicação: {formatLocalDateTime(publication.publishedAt)}</p>
              <Link className="text-link" href={`/publicacoes/${publication.id}`}>Ver detalhes →</Link>
            </li>)}</ul>
            {results.length === 50 && <p className="muted text-sm">Exibindo até 50 resultados. Refine a pesquisa para encontrar outras publicações.</p>}
          </>}
      </div>}
    </section>
    <section className="home-features" aria-label="O que você encontra na consulta">
      {[ ["01", "Documento oficial", "Fonte, datas e acesso à publicação original."],
        ["02", "Impacto no ERP", "Resumo técnico e ações recomendadas pela análise."],
        ["03", "Evidências", "Trechos e referências que dão contexto às recomendações."] ].map(([number, title, text]) =>
        <article key={number}><span className="feature-number">{number}</span><h3>{title}</h3><p>{text}</p></article>)}
    </section>
    <p className="muted text-sm">As recomendações apoiam a avaliação técnica e precisam ser conferidas pela sua equipe.</p>
  </div>;
}
