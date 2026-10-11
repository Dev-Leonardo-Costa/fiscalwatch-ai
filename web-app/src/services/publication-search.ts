import { z } from "zod";
import { publicationSchema } from "../types/fiscal";
import { createHttpClient } from "./http";

export function queryPublications(client: ReturnType<typeof createHttpClient>, query: string) {
  return client.get(`/api/publications?q=${encodeURIComponent(query.trim())}`, z.array(publicationSchema));
}
