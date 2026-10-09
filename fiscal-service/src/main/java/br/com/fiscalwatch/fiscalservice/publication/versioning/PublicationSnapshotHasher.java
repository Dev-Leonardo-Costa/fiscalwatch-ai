package br.com.fiscalwatch.fiscalservice.publication.versioning;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;
import br.com.fiscalwatch.fiscalservice.publication.messaging.PublicationDocumentEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** Contrato experimental v1, isolado do processamento e da persistência. */
public final class PublicationSnapshotHasher {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS", java.util.Locale.ROOT);

    private PublicationSnapshotHasher() { }

    public static SnapshotHash calculate(PublicationRequest publication,
                                         PublicationDocumentEvent document) {
        Objects.requireNonNull(publication, "Publicação obrigatória");
        String contentHash = null;
        if (document != null) {
            if (document.extractionStatus() != ExtractionStatus.EXTRACTED
                    || document.contentText() == null || document.contentText().isBlank()
                    || document.extractionError() != null) {
                throw new IllegalArgumentException("Documento não é candidato a snapshot válido");
            }
            contentHash = calculateContentHash(document.contentText());
            String declared = document.contentHash();
            if (declared != null && (!declared.matches("[0-9a-fA-F]{64}")
                    || !contentHash.equalsIgnoreCase(declared))) {
                throw new IllegalArgumentException("Hash de conteúdo inválido ou inconsistente");
            }
        }

        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("external_id", publication.externalId());
        fields.put("source", publication.source());
        fields.put("title", publication.title());
        fields.put("document_type", publication.documentType() == null
                ? null : publication.documentType().name());
        fields.put("published_at", date(publication.publishedAt()));
        fields.put("modified_at", date(publication.modifiedAt()));
        fields.put("description", publication.description());
        fields.put("download_url", publication.downloadUrl());
        fields.put("document_source_url", document == null ? null : document.sourceUrl());
        fields.put("document_content_hash", contentHash);

        StringBuilder canonical = new StringBuilder("fiscalwatch.publication-snapshot:v1\n");
        fields.forEach((key, value) -> {
            canonical.append(key).append(':');
            if (value == null) {
                canonical.append("-1:");
            } else {
                canonical.append(utf8(value).length).append(':').append(value);
            }
            canonical.append('\n');
        });
        return new SnapshotHash(calculateContentHash(canonical.toString()), contentHash,
                canonical.toString());
    }

    /** SHA-256 dos bytes UTF-8 exatos, equivalente a hashlib.sha256(text.encode('utf-8')). */
    public static String calculateContentHash(String text) {
        Objects.requireNonNull(text, "Texto obrigatório");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(utf8(text)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static byte[] utf8(String text) {
        // Python rejeita surrogates isolados; Java não deve substituí-los silenciosamente por '?'.
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    throw new IllegalArgumentException("Unicode inválido");
                }
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Unicode inválido");
            }
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String date(LocalDateTime value) {
        return value == null ? null : DATE.format(value);
    }

    public record SnapshotHash(String snapshotHash, String contentHash, String canonicalSnapshot) { }
}
