import type { ImpactAnalysis } from "@/types/fiscal";
import { analysisKind, formatLocalDateTime } from "@/lib/formatters";
import { Badge, ExternalLink } from "./ui";
import { DeadlineSection } from "./deadline-section";

export function AnalysisCard({ analysis: a }: { analysis: ImpactAnalysis }) {
  const impacts = a.technicalImpacts ?? [], actions = a.actionItems ?? [], evidences = a.evidences ?? [];
  return <article className="panel analysis-card">
    <header className="analysis-heading"><div><span className="eyebrow">ANÁLISE #{a.id} · {analysisKind(a.analysisVersion)}</span><h3>Impacto e próximos passos</h3><p className="muted text-sm">{a.analysisVersion ?? "Versão não informada"} · {formatLocalDateTime(a.analyzedAt)}</p></div><Badge value={a.status} /></header>
    <DeadlineSection homologation={a.homologationDeadline} production={a.productionDeadline} />
    <section className="analysis-section technical-level"><h4>Nível de impacto técnico</h4><Badge value={a.impactLevel} /><p className="muted text-sm">Classificação da mudança técnica e do esforço de adequação.</p></section>
    <p className="analysis-summary">{a.summary ?? "Resumo não informado."}</p>
    <section className="analysis-section"><h4>Impactos técnicos <span className="count">{impacts.length}</span></h4>{impacts.length ? impacts.map(i => <div className="impact-row" key={i.id}><div><h5>{i.title ?? "Impacto técnico"}</h5><p>{i.description ?? "Descrição não informada."}</p><span className="muted text-sm">{i.affectedArea ?? "Área não informada"} · {i.affectedComponent ?? "Componente não informado"}</span></div><Badge value={i.impactLevel} /></div>) : <p className="muted">Nenhum impacto técnico registrado.</p>}</section>
    <section className="analysis-section"><h4>Ações recomendadas <span className="count">{actions.length}</span></h4>{actions.length ? <ol className="action-list">{actions.map((i, index) => <li key={i.id}><span className="action-number" aria-hidden="true">{index + 1}</span><div><h5>{i.title ?? "Ação recomendada"}</h5><p>{i.description ?? "Descrição não informada."}</p><p className="muted text-sm">{i.targetProfessionalProfile ?? "Perfil não informado"} · Prazo: {formatLocalDateTime(i.dueAt)} · {i.completed === null ? "Conclusão não informada" : i.completed ? "Concluída" : "Não concluída"}</p></div><Badge value={i.priority} /></li>)}</ol> : <p className="muted">Nenhuma ação registrada.</p>}</section>
    <section className="analysis-section"><h4>Evidências <span className="count">{evidences.length}</span></h4>{evidences.length ? evidences.map(e => <div className="evidence" key={e.id}><div className="evidence-heading"><strong>{e.sourceTitle ?? "Referência oficial"}</strong><ExternalLink url={e.sourceUrl}>Abrir fonte</ExternalLink></div><blockquote>{e.excerpt ?? "Trecho não informado."}</blockquote><p className="muted text-sm">{e.documentSection ? `Seção: ${e.documentSection} · ` : ""}{e.pageNumber !== null ? `Página ${e.pageNumber} · ` : ""}{e.startPosition !== null && e.endPosition !== null ? `Posições ${e.startPosition}–${e.endPosition}` : "Posição não informada"}</p></div>) : <p className="muted">Nenhuma evidência registrada.</p>}</section>
    <p className="muted text-xs">Criada em {formatLocalDateTime(a.createdAt)} · Atualizada em {formatLocalDateTime(a.updatedAt)}</p>
  </article>;
}
