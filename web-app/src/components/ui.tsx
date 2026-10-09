import { safeExternalUrl, label } from "@/lib/formatters";

export function Badge({ value }: { value: string | null }) {
  return <span className={`badge badge-${value?.toLowerCase() ?? "neutral"}`}>{label(value)}</span>;
}
export function ExternalLink({ url, children }: { url: string | null; children: React.ReactNode }) {
  const safe = safeExternalUrl(url);
  return safe ? <a className="external-link" href={safe} target="_blank" rel="noopener noreferrer">{children} <span aria-hidden="true">↗</span></a>
    : <span className="muted">Link não disponível</span>;
}
export function Notice({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="notice" role="status"><strong>{title}</strong><p>{children}</p></div>;
}
