package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.rules;

import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationAnalysisInput;
import br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer.PublicationDocumentAnalysisInput;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import br.com.fiscalwatch.fiscalservice.publication.enums.ExtractionStatus;

import java.util.Locale;

public record DocumentContext(

        String title,
        String description,
        DocumentType documentType,
        String publicationDownloadUrl,
        String documentContentText,
        String documentSourceUrl,
        boolean documentExtracted
) {

    public static DocumentContext from(PublicationAnalysisInput input) {
        PublicationDocumentAnalysisInput document = input.document();
        boolean extracted = document != null
                && document.extractionStatus() == ExtractionStatus.EXTRACTED
                && document.contentText() != null
                && !document.contentText().isBlank();

        return new DocumentContext(
                input.title(),
                input.description(),
                input.documentType(),
                input.downloadUrl(),
                extracted ? document.contentText() : null,
                document == null ? null : document.sourceUrl(),
                extracted
        );
    }

    public String analyzableText() {
        StringBuilder text = new StringBuilder();

        appendIfNotBlank(text, title);
        appendIfNotBlank(text, description);

        if (documentExtracted) {
            appendIfNotBlank(text, documentContentText);
        }

        return text.toString();
    }

    public String lowerAnalyzableText() {
        return analyzableText().toLowerCase(Locale.ROOT);
    }

    public String documentSourceUrlOrFallback() {
        if (documentSourceUrl != null && !documentSourceUrl.isBlank()) {
            return documentSourceUrl;
        }

        return publicationDownloadUrl;
    }

    private static void appendIfNotBlank(StringBuilder text, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!text.isEmpty()) {
            text.append(' ');
        }

        text.append(value);
    }
}
