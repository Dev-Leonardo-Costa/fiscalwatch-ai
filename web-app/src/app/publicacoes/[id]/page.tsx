import Link from "next/link";
import { notFound } from "next/navigation";
import { getPublication, getPublicationAnalyses } from "@/services/fiscal-service";
import { ApiError } from "@/services/http";
import { formatLocalDateTime, isPublicationId, label } from "@/lib/formatters";
import { AnalysisCard } from "@/components/analysis-card";
import { DeadlineSection } from "@/components/deadline-section";
import { ExternalLink, Notice } from "@/components/ui";

export const dynamic = "force-dynamic";

export default async function PublicationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!isPublicationId(id)) notFound();
  let publication;
  try { publication = await getPublication(Number(id)); }
  catch (error) {
    if (error instanceof ApiError && error.kind === "not-found") notFound();
    if (!(error instanceof ApiError)) throw error;
    return <div className="page-stack"><Link href="/">← Voltar ao início</Link><h1>Não foi possível consultar a publicação</h1><Notice title="Consulta indisponível">{error.message}</Notice><a className="button" href={`/publicacoes/${id}`}>Tentar novamente</a></div>;
  }
  let analyses: Awaited<ReturnType<typeof getPublicationAnalyses>> = [];
  let analysisError: string | null = null;
  try { analyses = await getPublicationAnalyses(Number(id)); }
  catch (error) {
    if (!(error instanceof ApiError)) throw error;
    analysisError = error.message;
  }
  return <div className="page-stack">
    <nav className="breadcrumbs" aria-label="Localização"><Link href="/">Início</Link><span>/</span><span>Publicação #{publication.id}</span></nav>
    <header className="publication-heading"><div><span className="eyebrow">{label(publication.source)} · {label(publication.documentType)}</span><h1>{publication.title}</h1></div><ExternalLink url={publication.downloadUrl}>Abrir documento oficial</ExternalLink></header>
    <section className="panel publication-info"><h2>Sobre a publicação</h2><p className="description">{publication.description ?? "Descrição não informada."}</p><dl className="metadata-grid">{[
      ["Fonte", label(publication.source)], ["Tipo", label(publication.documentType)],
      ["Publicada em", formatLocalDateTime(publication.publishedAt)], ["Modificada em", formatLocalDateTime(publication.modifiedAt)],
      ["Registrada em", formatLocalDateTime(publication.createdAt)], ["Identificador externo", publication.externalId],
    ].map(([name, value]) => <div key={name}><dt>{name}</dt><dd>{value}</dd></div>)}</dl><p className="muted text-xs">Datas exibidas conforme recebidas do serviço fiscal, sem conversão de fuso.</p></section>
    <div className="section-heading"><div><span className="eyebrow">INTELIGÊNCIA APLICADA</span><h2>Análises de impacto</h2></div><a className="text-link" href={`/publicacoes/${id}`}>Atualizar consulta ↻</a></div>
    {!analyses.length && <div className="panel"><DeadlineSection /><p className="muted text-sm">{analysisError ? "Não foi possível verificar os prazos: consulta de análises indisponível." : "Não há análise disponível para identificar os prazos desta publicação."}</p></div>}
    {analysisError ? <Notice title="As análises não puderam ser carregadas">{analysisError} Os dados da publicação continuam disponíveis.</Notice>
      : analyses.length ? analyses.map(a => <AnalysisCard key={a.id} analysis={a} />)
      : <Notice title="Nenhuma análise disponível">Esta publicação ainda não possui análise registrada. Uma análise pode aparecer em uma consulta posterior.</Notice>}
    <p className="muted text-sm">Avalie as recomendações e seus prazos à luz do documento oficial e do contexto do seu ERP.</p>
  </div>;
}
