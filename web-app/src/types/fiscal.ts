
import { z } from "zod";

// Contratos dos records Java em publication/dto e impactanalysis/dto.
export const impactLevelSchema = z.enum(["NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL"]);
const text = z.string().nullable();
const id = z.number().int().positive().max(Number.MAX_SAFE_INTEGER);
const technicalImpactSchema = z.object({
  id, title: text, description: text, affectedArea: text, affectedComponent: text,
  impactLevel: impactLevelSchema.nullable(), createdAt: text,
});
const actionItemSchema = z.object({
  id, title: text, description: text, targetProfessionalProfile: text,
  priority: impactLevelSchema.nullable(), dueAt: text, completed: z.boolean().nullable(), createdAt: text,
});
const evidenceSchema = z.object({
  id, sourceTitle: text, sourceUrl: text, documentSection: text,
  pageNumber: z.number().int().nullable(), excerpt: text,
  startPosition: z.number().int().nullable(), endPosition: z.number().int().nullable(), createdAt: text,
});
export const publicationSchema = z.object({
  id, externalId: z.string(), source: z.string(), title: z.string(),
  documentType: z.enum(["NOTA_TECNICA", "INFORME_TECNICO", "DOCUMENTO_TECNICO", "NOTICIA",
    "TABELA", "ATO_TECNICO", "ATO_NORMATIVO", "ORIENTACAO", "SCHEMA", "MANUAL", "OUTRO"]).nullable(),
  publishedAt: text, modifiedAt: text, description: text, downloadUrl: text, createdAt: text,
});
export const analysisSchema = z.object({
  id, publicationId: id, summary: text, impactLevel: impactLevelSchema.nullable(),
  status: z.enum(["PENDING", "IN_PROGRESS", "COMPLETED", "FAILED", "REVIEWED"]).nullable(),
  analysisVersion: text, homologationDeadline: text, productionDeadline: text,
  analyzedAt: text, technicalImpacts: z.array(technicalImpactSchema).nullable(),
  actionItems: z.array(actionItemSchema).nullable(), evidences: z.array(evidenceSchema).nullable(),
  createdAt: text, updatedAt: text,
});
export type Publication = z.infer<typeof publicationSchema>;
export type ImpactAnalysis = z.infer<typeof analysisSchema>;
export type ImpactLevel = z.infer<typeof impactLevelSchema>;
