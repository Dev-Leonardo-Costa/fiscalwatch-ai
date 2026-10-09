import assert from "node:assert/strict";
import { test } from "node:test";
import { analysisKind, formatLocalDateTime, safeExternalUrl, isPublicationId } from "../src/lib/formatters";
import { deadlineDetails } from "../src/lib/deadlines";

test("prazos válidos são classificados por dia em Fortaleza", () => {
  const now = new Date("2026-10-10T02:30:00Z"); // Ainda 09/10 em Fortaleza.
  assert.equal(deadlineDetails("2026-10-08T23:59:59", now).label, "Vencido");
  assert.equal(deadlineDetails("2026-10-10T00:00:00", now).label, "Futuro");
  assert.equal(deadlineDetails("2026-10-09T00:00:00", now).label, "Hoje");
  assert.equal(deadlineDetails("2026-10-09T23:59:59.123456789", now).label, "Hoje");
});

test("prazos ausentes ou inválidos não recebem situação temporal", () => {
  for (const value of [null, "", "not-a-date", "2026-02-30T00:00:00", "2026-13-01T00:00:00", "2026-10-09", "2026-10-09T00:00:00Z"])
    assert.deepEqual(deadlineDetails(value), { date: null, label: "Prazo não identificado", status: "unknown" });
  assert.equal(deadlineDetails("2024-02-29T00:00:00", new Date("2024-02-28T12:00:00Z")).label, "Futuro");
});

test("mantém LocalDateTime inclusive microssegundos sem conversão de fuso", () => {
  assert.equal(formatLocalDateTime("2026-10-09T20:16:02.427417"), "09/10/2026, 20:16");
  assert.equal(formatLocalDateTime("2024-02-29T00:00:00"), "29/02/2024, 00:00");
});
test("datas nulas e inválidas não quebram a página", () => {
  assert.equal(formatLocalDateTime(null), "Não informado");
  for (const date of ["2025-02-29T00:00:00", "2026-13-01T00:00:00", "2026-01-01T24:00:00", "not-a-date", "2026-01-01T00:00:00Z"])
    assert.equal(formatLocalDateTime(date), "Data indisponível");
});
test("links aceitam apenas HTTP e HTTPS sem credenciais", () => {
  assert.equal(safeExternalUrl("https://example.test/document.pdf"), "https://example.test/document.pdf");
  for (const url of [null, "javascript:alert(1)", "data:text/html,unsafe", "/relative", "https://user:secret@example.test"])
    assert.equal(safeExternalUrl(url), null);
});
test("classifica pela versão sem confundir tipo de publicação com análise", () => {
  assert.equal(analysisKind("automatic-v1"), "Análise documental");
  assert.equal(analysisKind("schema-comparison-v2"), "Comparação de schemas");
  assert.equal(analysisKind("future-v2"), "Análise · versão não reconhecida");
});
test("IDs inválidos ou sem precisão numérica não são consultados", () => {
  assert.ok(isPublicationId("94"));
  for (const id of ["-1", "0", "01", "94x", "9007199254740992", "../94"])
    assert.equal(isPublicationId(id), false);
});
