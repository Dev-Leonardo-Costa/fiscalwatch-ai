import { formatLocalDateTime } from "./formatters";

// Recognizes the extractor's environment-specific evidence, never confirmation.
export function hasDeadlineEvidence(evidences: { documentSection: string | null }[], environment: "homologação" | "produção") {
  return evidences.some(({ documentSection: section }) =>
    section != null && (section.startsWith("Cronograma;") || section.startsWith("Prazo documental;"))
    && section.includes(`; ${environment};`));
}

// Legacy dates and documentary excerpts do not certify continuing validity.
// No current API field supplies confirmation; callers default to unconfirmed.
export function deadlineDetails(value: string | null, now = new Date(),
  confirmation: "confirmed" | "unconfirmed" = "unconfirmed", hasDocumentaryEvidence = false) {
  if (!value || formatLocalDateTime(value) === "Data indisponível") {
    return { date: null, label: value || hasDocumentaryEvidence ? "Prazo não confirmado" : "Não informado", status: "unknown" } as const;
  }
  if (confirmation !== "confirmed")
    return { date: null, label: "Prazo não confirmado", status: "unknown" } as const;
  const date = value.slice(0, 10);
  // O Java fornece LocalDateTime e a extração usa início do dia: compare dias,
  // sem interpretar meia-noite como um horário limite ou presumir UTC.
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Fortaleza", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(now);
  const part = (type: string) => parts.find(p => p.type === type)!.value;
  const today = `${part("year")}-${part("month")}-${part("day")}`;
  return {
    date,
    label: date < today ? "Vencido" : date > today ? "Futuro" : "Hoje",
    status: date < today ? "expired" : date > today ? "future" : "today",
  } as const;
}
