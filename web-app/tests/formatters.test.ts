import assert from "node:assert/strict";
import { test } from "node:test";
import { analysisKind, formatLocalDateTime, safeExternalUrl, isPublicationId } from "../src/lib/formatters";
import { deadlineDetails, hasDeadlineEvidence } from "../src/lib/deadlines";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { DeadlineSection } from "../src/components/deadline-section";

test("somente prazos confirmados são classificados por dia em Fortaleza", () => {
  const now = new Date("2026-10-10T02:30:00Z"); // Ainda 09/10 em Fortaleza.
  assert.equal(deadlineDetails("2026-10-08T23:59:59", now, "confirmed").label, "Vencido");
  assert.equal(deadlineDetails("2026-10-10T00:00:00", now, "confirmed").label, "Futuro");
  assert.equal(deadlineDetails("2026-10-09T00:00:00", now, "confirmed").label, "Hoje");
  assert.equal(deadlineDetails("2026-10-09T23:59:59.123456789", now, "confirmed").label, "Hoje");
});

test("prazos ausentes ou inválidos não recebem situação temporal", () => {
  for (const value of [null, "", "not-a-date", "2026-02-30T00:00:00", "2026-13-01T00:00:00", "2026-10-09", "2026-10-09T00:00:00Z"])
    assert.deepEqual(deadlineDetails(value), { date: null, label: value ? "Prazo não confirmado" : "Não informado", status: "unknown" });
  assert.equal(deadlineDetails("2024-02-29T00:00:00", new Date("2024-02-28T12:00:00Z"), "confirmed").label, "Futuro");
});

test("datas legadas da publicação 90 não são promovidas a prazos vencidos", () => {
  const now = new Date("2026-10-11T12:00:00Z");
  for (const value of ["2026-09-17T00:00:00", "2027-01-01T00:00:00"])
    assert.deepEqual(deadlineDetails(value, now), { date: null, label: "Prazo não confirmado", status: "unknown" });
  assert.equal(deadlineDetails(null, now, "unconfirmed", true).label, "Prazo não confirmado");
  assert.equal(deadlineDetails(null, now).label, "Não informado");
});

test("evidência histórica de produção não confirma prazo nem informa homologação", () => {
  const evidence = [{ documentSection: "Cronograma; versão 1.21; produção; histórico, vigência não confirmada" }];
  assert.equal(hasDeadlineEvidence(evidence, "produção"), true);
  assert.equal(hasDeadlineEvidence(evidence, "homologação"), false);
  assert.equal(hasDeadlineEvidence([{ documentSection: null }], "produção"), false);
  assert.equal(hasDeadlineEvidence([{ documentSection: "Revisar produção; CFOP" }], "produção"), false);
});

test("seção renderizada preserva ambientes sem exibir datas legadas como vencidas", () => {
  const html = renderToStaticMarkup(createElement(DeadlineSection, {
    homologation: "2026-09-17T00:00:00", production: "2026-09-17T00:00:00",
    now: new Date("2026-10-11T12:00:00Z"),
  }));
  assert.match(html, /Prazo de homologação/);
  assert.match(html, /Prazo de produção/);
  assert.equal((html.match(/Prazo não confirmado/g) ?? []).length, 2);
  assert.doesNotMatch(html, /Vencido|17\/09\/2026|<time/);
  const missing = renderToStaticMarkup(createElement(DeadlineSection));
  assert.equal((missing.match(/Não informado/g) ?? []).length, 2);
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
