import { PublicationSearch } from "@/components/publication-search";

export default function Home() {
  return <div className="home">
    <div className="eyebrow">INTELIGÊNCIA FISCAL PARA EQUIPES DE TECNOLOGIA</div>
    <h1>Da publicação oficial<br />à decisão técnica.</h1>
    <p className="lead">Consulte documentos fiscais e entenda os impactos, as ações recomendadas e as evidências que apoiam a evolução do seu ERP.</p>
    <section className="panel search-panel" id="consultar"><div className="section-heading"><div><span className="eyebrow">CONSULTA</span><h2>Explore uma publicação</h2></div><span className="panel-icon" aria-hidden="true">↗</span></div><PublicationSearch /></section>
    <section className="home-features" aria-label="O que você encontra na consulta">
      {[ ["01", "Documento oficial", "Fonte, datas e acesso à publicação original."],
        ["02", "Impacto no ERP", "Resumo técnico e ações recomendadas pela análise."],
        ["03", "Evidências", "Trechos e referências que dão contexto às recomendações."] ].map(([number, title, text]) =>
        <article key={number}><span className="feature-number">{number}</span><h3>{title}</h3><p>{text}</p></article>)}
    </section>
    <p className="muted text-sm">As recomendações apoiam a avaliação técnica e precisam ser conferidas pela sua equipe.</p>
  </div>;
}
