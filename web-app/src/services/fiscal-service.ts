import "server-only";
import { z } from "zod";
import { createHttpClient } from "./http";
import { publicationSchema, analysisSchema } from "@/types/fiscal";

const client = () => createHttpClient(process.env.FISCAL_SERVICE_BASE_URL);
export function getPublication(id: number) {
  return client().get(`/api/publications/${id}`, publicationSchema);
}
export function getPublicationAnalyses(id: number) {
  return client().get(`/api/impact-analyses/publications/${id}`, z.array(analysisSchema));
}
