"use client";
import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";

export function PublicationSearch({ query = "" }: { query?: string }) {
  const router = useRouter();
  const [error, setError] = useState("");
  const [pending, startTransition] = useTransition();
  return <form onSubmit={(event) => {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get("q") ?? "").trim();
    if (!value || value.length > 200) { setError("Informe uma pesquisa de 1 a 200 caracteres."); return; }
    setError(""); startTransition(() => {
      if (value === query) router.refresh();
      else router.push(`/?q=${encodeURIComponent(value)}`);
    });
  }}>
    <label htmlFor="publication-query" className="field-label">Número da NT, ano, título ou palavra-chave</label>
    <div className="search-row"><input id="publication-query" name="q" defaultValue={query} maxLength={200} autoComplete="off"
      placeholder="Ex.: 2021.003, 2023 ou GTIN" aria-describedby={error ? "search-error" : "search-help"} aria-invalid={!!error} />
      <button className="button" type="submit" disabled={pending}>{pending ? "Pesquisando…" : "Pesquisar publicações"} <span aria-hidden="true">→</span></button></div>
    {pending && <p className="muted text-sm" role="status">Buscando publicações…</p>}
    <p id={error ? "search-error" : "search-help"} className={error ? "form-error" : "muted text-sm"} role={error ? "alert" : undefined}>
      {error || "Pesquise publicações disponíveis no FiscalWatch AI."}</p>
  </form>;
}
