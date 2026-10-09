import { z } from "zod";

export class ApiError extends Error {
  constructor(public readonly kind: "configuration" | "not-found" | "unavailable" | "invalid-response",
              public readonly status?: number) {
    super({ configuration: "A conexão com o serviço fiscal não está configurada.",
      "not-found": "O registro solicitado não foi encontrado.",
      unavailable: "O serviço fiscal está indisponível. Tente novamente em instantes.",
      "invalid-response": "O serviço fiscal retornou uma resposta incompatível." }[kind]);
    this.name = "ApiError";
  }
}

export function createHttpClient(baseUrl: string | undefined, fetcher: typeof fetch = fetch, timeoutMs = 10_000) {
  let base: URL;
  try {
    base = new URL(baseUrl ?? "");
    if (!["http:", "https:"].includes(base.protocol) || base.username || base.password || base.search || base.hash)
      throw new Error();
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new Error();
  } catch { throw new ApiError("configuration"); }
  const prefix = base.href.replace(/\/$/, "");
  return {
    async get<T>(path: string, schema: z.ZodType<T>): Promise<T> {
      if (!path.startsWith("/api/")) throw new ApiError("configuration");
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), timeoutMs);
      try {
        const response = await fetcher(`${prefix}${path}`, {
          method: "GET", headers: { Accept: "application/json" }, cache: "no-store",
          redirect: "error", signal: controller.signal,
        });
        if (response.status === 404) throw new ApiError("not-found", 404);
        if (!response.ok) throw new ApiError("unavailable", response.status);
        let body: unknown;
        try { body = await response.json(); }
        catch { throw new ApiError(controller.signal.aborted ? "unavailable" : "invalid-response"); }
        const parsed = schema.safeParse(body);
        if (!parsed.success) throw new ApiError("invalid-response");
        return parsed.data;
      } catch (error) {
        if (error instanceof ApiError) throw error;
        throw new ApiError("unavailable");
      } finally { clearTimeout(timer); }
    },
  };
}
