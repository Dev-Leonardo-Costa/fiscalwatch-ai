import { deadlineDetails } from "@/lib/deadlines";

export function DeadlineSection({ homologation = null, production = null, now = new Date() }: {
  homologation?: string | null; production?: string | null; now?: Date;
}) {
  return <section className="deadline-section" aria-label="Prazos de implantação">
    <span className="eyebrow">CRONOGRAMA DE IMPLANTAÇÃO</span>
    <h4>Prazos de homologação e produção</h4>
    <dl className="deadline-grid">{[
      ["Prazo de homologação", homologation], ["Prazo de produção", production],
    ].map(([title, value]) => {
      const deadline = deadlineDetails(value, now);
      return <div className="deadline-card" key={title}>
        <dt>{title}</dt><dd>
          {deadline.date ? <><time dateTime={deadline.date}>{deadline.date.split("-").reverse().join("/")}</time><span className={`badge deadline-${deadline.status}`}>{deadline.label}</span></>
            : <strong>Prazo não identificado</strong>}
        </dd>
      </div>;
    })}</dl>
    <p className="muted text-xs">Datas informadas pela análise fiscal; podem corresponder a cronogramas históricos. Confirme a versão e os prazos no documento oficial. Situação por dia, com referência em Fortaleza (UTC−3); o dia atual é indicado como “Hoje”.</p>
  </section>;
}
