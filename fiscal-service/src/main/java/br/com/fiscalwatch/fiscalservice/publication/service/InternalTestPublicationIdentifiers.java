package br.com.fiscalwatch.fiscalservice.publication.service;

import java.util.regex.Pattern;

/** Explicit fixture namespaces observed locally. Never classifies by title or keywords. */
public final class InternalTestPublicationIdentifiers {
    private static final Pattern JOB_ID = Pattern.compile("it-svrs-job-[0-9a-f]{32}-(?:valid|failed)");
    private static final Pattern FIXTURE_URL = Pattern.compile("https://fixtures\\.invalid/svrs/mvp-[0-9a-f]{32}\\.pdf");
    private InternalTestPublicationIdentifiers() { }

    public static boolean matches(String source, String externalId, String downloadUrl) {
        return "INTEGRATION_TEST".equalsIgnoreCase(source)
                || ("SVRS".equalsIgnoreCase(source)
                && ((externalId != null && JOB_ID.matcher(externalId).matches())
                || (downloadUrl != null && FIXTURE_URL.matcher(downloadUrl).matches())));
    }
}
