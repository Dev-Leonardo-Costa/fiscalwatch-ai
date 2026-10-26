import { deadlineDetails } from "@/lib/deadlines";

export function DeadlineSection({ homologation = null, production = null, homologationEvidence = false, productionEvidence = false, now = new Date() }: {
  homologation?: string | null; production?: string | null; homologationEvidence?: boolean; productionEvidence?: boolean; now?: Date;
}) {
  return <section className="deadline-section" aria-label="Prazos de implantação">
    <span className="eyebrow">CRONOGRAMA DE IMPLANTAÇÃO</span>
    <h4>Prazos de homologação e produção</h4>
    <dl className="deadline-grid">{[
      { title: "Prazo de homologação", value: homologation, evidence: homologationEvidence },
      { title: "Prazo de produção", value: production, evidence: productionEvidence },
    ].map(({ title, value, evidence }) => {
      const deadline = deadlineDetails(value, now, "unconfirmed", evidence);
      return <div className="deadline-card" key={title}>
        <dt>{title}</dt><dd>
          {deadline.date ? <><time dateTime={deadline.date}>{deadline.date.split("-").reverse().join("/")}</time><span className={`badge deadline-${deadline.status}`}>{deadline.label}</span></>
            : <strong>{deadline.label}</strong>}
        </dd>
      </div>;
    })}</dl>
    <p className="muted text-xs">A análise não fornece confirmação de vigência dos prazos. Datas legadas e cronogramas históricos não recebem situação temporal. Consulte a versão e o cronograma no documento oficial.</p>
  </section>;
}
