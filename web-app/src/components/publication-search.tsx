"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { isPublicationId } from "@/lib/formatters";

export function PublicationSearch() {
  const router = useRouter();
  const [error, setError] = useState("");
  return <form onSubmit={(event) => {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get("id") ?? "").trim();
    if (!isPublicationId(value)) { setError("Informe um identificador numérico válido."); return; }
    setError(""); router.push(`/publicacoes/${value}`);
  }}>
    <label htmlFor="publication-id" className="field-label">Identificador da publicação</label>
    <div className="search-row"><input id="publication-id" name="id" inputMode="numeric" autoComplete="off"
      placeholder="Digite o ID" aria-describedby={error ? "search-error" : "search-help"} aria-invalid={!!error} />
      <button className="button" type="submit">Consultar publicação <span aria-hidden="true">→</span></button></div>
    <p id={error ? "search-error" : "search-help"} className={error ? "form-error" : "muted text-sm"} role={error ? "alert" : undefined}>
      {error || "Use o ID de uma publicação já persistida no FiscalWatch AI."}</p>
  </form>;
}
