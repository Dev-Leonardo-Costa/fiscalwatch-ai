package br.com.fiscalwatch.fiscalservice.publication.versioning;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import br.com.fiscalwatch.fiscalservice.publication.enums.DocumentType;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative resolver: unknown/ambiguous documents are retained without a guessed identity. */
public record TechnicalNoteIdentity(String family, int year, int number, String version) {
    private static final Pattern NOTE = Pattern.compile(
            "(?iu)\\b(?:nota\\s+t[eé]cnica|nt)\\s*(?:n[º°o.]?\\s*)?(20\\d{2})[. /-](\\d{1,3})(?!\\d)");
    private static final Pattern VERSION = Pattern.compile(
            "(?iu)\\b(?:vers[aã]o\\s*|v\\.?\\s*)(\\d{1,3}\\.\\d{2})(?![\\d.])");

    public static Optional<TechnicalNoteIdentity> resolve(PublicationRequest request, String text) {
        if (request.documentType() != DocumentType.NOTA_TECNICA) return Optional.empty();
        String source = request.source();
        if (!Set.of("PORTAL_NFE", "SVRS").contains(source)) return Optional.empty();
        String context = (request.title() + " " + request.downloadUrl()).toUpperCase(Locale.ROOT);
        // SVRS also publishes CT-e/MDF-e. Never assign those to the NF-e family by source alone.
        if (context.matches("(?s).*(?:CT-E|CTE/|MDF-E|MDFE/).*")) return Optional.empty();
        if (!source.equals("PORTAL_NFE") && !context.contains("/NFE/")
                && !context.contains("NF-E") && !context.contains("NFC-E")) return Optional.empty();
        String cover = text.substring(0, Math.min(text.length(), 800));
        cover = cover.split("(?iu)sum[áa]rio|hist[óo]rico de altera", 2)[0];
        Set<String> documentNotes = matches(NOTE, cover, true);
        Set<String> titleNotes = matches(NOTE, request.title(), true);
        Set<String> versions = matches(VERSION, cover, false);
        if (documentNotes.size() > 1 || titleNotes.size() > 1 || versions.size() > 1) return Optional.empty();
        if (!documentNotes.isEmpty() && !titleNotes.isEmpty() && !documentNotes.equals(titleNotes)) return Optional.empty();
        Set<String> notes = documentNotes.isEmpty() ? titleNotes : documentNotes;
        if (versions.isEmpty()) versions = matches(VERSION, request.title(), false);
        if (notes.size() != 1 || versions.size() != 1) return Optional.empty();
        String[] number = notes.iterator().next().split(":");
        if (Integer.parseInt(number[1]) == 0) return Optional.empty();
        return Optional.of(new TechnicalNoteIdentity("NFE_NFCE", Integer.parseInt(number[0]),
                Integer.parseInt(number[1]), versions.iterator().next()));
    }

    private static Set<String> matches(Pattern pattern, String value, boolean number) {
        Set<String> result = new HashSet<>();
        var matcher = pattern.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(number ? matcher.group(1) + ":" + Integer.parseInt(matcher.group(2)) : matcher.group(1));
        return result;
    }
}
