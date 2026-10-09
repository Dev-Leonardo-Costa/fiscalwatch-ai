import { formatLocalDateTime } from "./formatters";

export function deadlineDetails(value: string | null, now = new Date()) {
  if (!value || formatLocalDateTime(value) === "Data indisponível") {
    return { date: null, label: "Prazo não identificado", status: "unknown" } as const;
  }
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
