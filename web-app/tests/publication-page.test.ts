import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";
import { z } from "zod";
import { createHttpClient, ApiError } from "../src/services/http";
import { publicationSchema, analysisSchema } from "../src/types/fiscal";
import * as formatters from "../src/lib/formatters";
import { AnalysisCard } from "../src/components/analysis-card";
import { DeadlineSection } from "../src/components/deadline-section";
import * as ui from "../src/components/ui";

// Execute the actual route source with framework/service boundaries injected.
// This avoids importing server-only in the Node test runner, while exercising
// the real route, HTTP client, response schemas and rendered components.
const source = readFileSync(new URL("../src/app/publicacoes/[id]/page.tsx", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true,
} }).outputText;
const nodeRequire = createRequire(import.meta.url);
const notFoundSignal = new Error("NEXT_HTTP_ERROR_FALLBACK;404");

function page(fetcher: typeof fetch) {
  const client = createHttpClient("http://localhost:8081", fetcher);
  const dependencies: Record<string, unknown> = {
    "next/link": { __esModule: true, default: (props: { href: string; children: ReactNode }) => createElement("a", props) },
    "next/navigation": { notFound: () => { throw notFoundSignal; } },
    "@/services/fiscal-service": {
      getPublication: (id: number) => client.get(`/api/publications/${id}`, publicationSchema),
      getPublicationAnalyses: (id: number) => client.get(`/api/impact-analyses/publications/${id}`, z.array(analysisSchema)),
    },
    "@/services/http": { ApiError }, "@/lib/formatters": formatters,
    "@/components/analysis-card": { AnalysisCard }, "@/components/deadline-section": { DeadlineSection },
    "@/components/ui": ui,
  };
  const exports: { default?: (props: { params: Promise<{ id: string }> }) => Promise<ReactNode> } = {};
  new Function("require", "exports", compiled)((name: string) => dependencies[name] ?? nodeRequire(name), exports);
  return exports.default!;
}

const publication = { id: 90, externalId: "a".repeat(64), source: "SVRS", title: "Nota Técnica 2026.009 v.1.00",
  documentType: "NOTA_TECNICA", publishedAt: "2026-09-09T00:00:00", modifiedAt: null,
  description: null, downloadUrl: null, createdAt: null };
const analysis = { id: 2, publicationId: 90, summary: null, impactLevel: "MEDIUM",
  status: "COMPLETED", analysisVersion: "automatic-v1", homologationDeadline: "2026-09-17T00:00:00",
  productionDeadline: "2026-09-17T00:00:00", analyzedAt: null, technicalImpacts: [], actionItems: [],
  evidences: [], createdAt: null, updatedAt: null };
const params = Promise.resolve({ id: "90" });

test("publicação existente resolve params assíncronos e preserva prazos não confirmados", async () => {
  const requests: string[] = [];
  const route = page(async (url, options) => {
    requests.push(String(url));
    assert.equal(options?.cache, "no-store");
    return Response.json(String(url).includes("impact-analyses") ? [analysis] : publication);
  });
  const html = renderToStaticMarkup(await route({ params }));
  assert.match(html, /Nota Técnica 2026\.009 v\.1\.00/);
  assert.equal((html.match(/Prazo não confirmado/g) ?? []).length, 2);
  assert.doesNotMatch(html, /Vencido|Publicação não encontrada/);
  assert.deepEqual(requests, ["http://localhost:8081/api/publications/90", "http://localhost:8081/api/impact-analyses/publications/90"]);
});

test("somente 404 da publicação aciona notFound e não consulta análises", async () => {
  const requests: string[] = [];
  const route = page(async url => { requests.push(String(url)); return new Response(null, { status: 404 }); });
  await assert.rejects(route({ params }), error => error === notFoundSignal);
  assert.deepEqual(requests, ["http://localhost:8081/api/publications/90"]);
});

for (const status of [404, 500]) {
  test(`falha HTTP ${status} de análises mantém a publicação visível`, async () => {
    const route = page(async url => String(url).includes("impact-analyses")
      ? new Response(null, { status }) : Response.json(publication));
    const html = renderToStaticMarkup(await route({ params }));
    assert.match(html, /Nota Técnica 2026\.009 v\.1\.00/);
    assert.match(html, /As análises não puderam ser carregadas/);
    assert.doesNotMatch(html, /Publicação não encontrada/);
  });
}

test("falhas de conexão e HTTP 500 da publicação mostram indisponibilidade, não 404", async () => {
  for (const fetcher of [async () => new Response(null, { status: 500 }),
    async () => { throw new TypeError("fetch failed"); }]) {
    const html = renderToStaticMarkup(await page(fetcher)({ params }));
    assert.match(html, /Não foi possível consultar a publicação/);
    assert.doesNotMatch(html, /Publicação não encontrada/);
  }
});

test("ID inválido aciona notFound antes de qualquer consulta", async () => {
  const route = page(async () => { assert.fail("Não deve consultar a API"); });
  await assert.rejects(route({ params: Promise.resolve({ id: "90x" }) }), error => error === notFoundSignal);
});
