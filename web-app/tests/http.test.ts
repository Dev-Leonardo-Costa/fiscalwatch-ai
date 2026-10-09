import assert from "node:assert/strict";
import { test } from "node:test";
import { z } from "zod";
import { ApiError, createHttpClient } from "../src/services/http";
import { publicationSchema, analysisSchema } from "../src/types/fiscal";

test("consulta somente GET no destino configurado sem cache", async () => {
  const fetcher: typeof fetch = async (url, options) => {
    assert.equal(url, "http://127.0.0.1:8081/api/publications/94");
    assert.equal(options?.method, "GET");
    assert.equal(options?.cache, "no-store");
    assert.equal(options?.redirect, "error");
    assert.ok(options?.signal);
    return Response.json({ id: 94 });
  };
  assert.deepEqual(await createHttpClient("http://127.0.0.1:8081/", fetcher).get("/api/publications/94", z.object({ id: z.number() })), { id: 94 });
});
for (const [status, kind] of [[404, "not-found"], [500, "unavailable"], [400, "unavailable"]] as const) {
  test(`HTTP ${status} sem corpo gera erro seguro`, async () => {
    const client = createHttpClient("http://localhost:8081", async () => new Response(null, { status }));
    await assert.rejects(client.get("/api/publications/94", z.object({})), (error: unknown) => error instanceof ApiError && error.kind === kind && error.status === status);
  });
}
test("não expõe mensagem interna do backend", async () => {
  const client = createHttpClient("http://localhost:8081", async () => Response.json({ message: "senha SQL sensível" }, { status: 500 }));
  await assert.rejects(client.get("/api/publications/94", z.object({})), (error: unknown) => error instanceof ApiError && !error.message.includes("SQL"));
});
for (const url of [undefined, "", "javascript:alert(1)", "http://user:secret@localhost:8081", "http://localhost:8081?x=1"]) {
  test(`rejeita configuração inválida ${url ?? "ausente"}`, () => {
    assert.throws(() => createHttpClient(url), ApiError);
  });
}
test("timeout aborta a consulta e retorna indisponibilidade", async () => {
  const fetcher: typeof fetch = async (_url, options) => new Promise((_resolve, reject) => {
    options?.signal?.addEventListener("abort", () => reject(new DOMException("Abortado", "AbortError")), { once: true });
  });
  await assert.rejects(createHttpClient("http://localhost", fetcher, 10).get("/api/publications/94", z.object({})), (error: unknown) => error instanceof ApiError && error.kind === "unavailable");
});
test("falha de rede não vira registro ausente", async () => {
  const client = createHttpClient("http://localhost", async () => { throw new TypeError("fetch failed"); });
  await assert.rejects(client.get("/api/publications/94", z.object({})), (error: unknown) => error instanceof ApiError && error.kind === "unavailable");
});
test("timeout durante leitura do corpo também indica indisponibilidade", async () => {
  const fetcher: typeof fetch = async (_url, options) => {
    const response = new Response();
    response.json = () => new Promise((_resolve, reject) => {
      options?.signal?.addEventListener("abort", () => reject(new DOMException("Abortado", "AbortError")), { once: true });
    });
    return response;
  };
  await assert.rejects(createHttpClient("http://localhost", fetcher, 10).get("/api/publications/94", z.object({})), (error: unknown) => error instanceof ApiError && error.kind === "unavailable");
});
test("resposta incompatível e JSON inválido são tratados", async () => {
  for (const response of [Response.json({ id: "94" }), new Response("not-json")]) {
    await assert.rejects(createHttpClient("http://localhost", async () => response).get("/api/publications/94", z.object({ id: z.number() })), (error: unknown) => error instanceof ApiError && error.kind === "invalid-response");
  }
});
test("lista vazia de análises é um sucesso", async () => {
  assert.deepEqual(await createHttpClient("http://localhost", async () => Response.json([])).get("/api/impact-analyses/publications/94", z.array(z.object({ id: z.number() }))), []);
});
test("contrato da publicação aceita metadados nulos sem inventar documento", () => {
  const publication = publicationSchema.parse({ id: 94, externalId: "a".repeat(64),
    source: "SVRS", title: "Publicação de teste", documentType: null, publishedAt: null,
    modifiedAt: null, description: null, downloadUrl: null, createdAt: null });
  assert.equal(publication.downloadUrl, null);
  assert.equal(publication.documentType, null);
});
test("contrato da análise aceita listas nulas e rejeita estados desconhecidos", () => {
  const analysis = { id: 5, publicationId: 94, summary: null, impactLevel: null,
    status: null, analysisVersion: "automatic-v1", homologationDeadline: null,
    productionDeadline: null, analyzedAt: null, technicalImpacts: null, actionItems: null,
    evidences: null, createdAt: null, updatedAt: null };
  assert.equal(analysisSchema.parse(analysis).evidences, null);
  assert.equal(analysisSchema.safeParse({ ...analysis, status: "UNKNOWN" }).success, false);
});
