export function formatLocalDateTime(value: string | null): string {
  if (!value) return "Não informado";
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(value);
  if (!match) return "Data indisponível";
  const [, year, month, day, hour, minute, second] = match;
  const y = Number(year), m = Number(month), d = Number(day);
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1] || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59)
    return "Data indisponível";
  // LocalDateTime não informa fuso. Mantém exatamente o horário recebido.
  return `${day}/${month}/${year}, ${hour}:${minute}`;
}

export function safeExternalUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ["https:", "http:"].includes(url.protocol) && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

export function analysisKind(version: string | null): string {
  if (version === "automatic-v1") return "Análise documental";
  if (version === "schema-comparison-v2") return "Comparação de schemas";
  return "Análise · versão não reconhecida";
}

export function isPublicationId(value: string): boolean {
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
}

const labels: Record<string, string> = {
  NONE: "Sem impacto", LOW: "Baixo", MEDIUM: "Médio", HIGH: "Alto", CRITICAL: "Crítico",
  PENDING: "Pendente", IN_PROGRESS: "Em andamento", COMPLETED: "Concluída", FAILED: "Falhou", REVIEWED: "Revisada",
  NOTA_TECNICA: "Nota técnica", INFORME_TECNICO: "Informe técnico", DOCUMENTO_TECNICO: "Documento técnico",
  NOTICIA: "Notícia", TABELA: "Tabela", ATO_TECNICO: "Ato técnico", ATO_NORMATIVO: "Ato normativo",
  ORIENTACAO: "Orientação", SCHEMA: "Schema", MANUAL: "Manual", OUTRO: "Outro", SVRS: "SVRS / DF-e",
};
export const label = (value: string | null) => value ? labels[value] ?? value : "Não informado";
