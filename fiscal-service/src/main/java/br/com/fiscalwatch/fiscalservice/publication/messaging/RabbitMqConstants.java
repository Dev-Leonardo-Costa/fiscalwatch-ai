package br.com.fiscalwatch.fiscalservice.publication.messaging;

public final class RabbitMqConstants {

    private RabbitMqConstants() {
    }

    public static final String PUBLICATION_EXCHANGE =
            "fiscalwatch.publications";

    public static final String PUBLICATION_DISCOVERED_QUEUE =
            "fiscal.publication.discovered";

    public static final String PUBLICATION_DISCOVERED_ROUTING_KEY =
            "publication.discovered";

    public static final String PUBLICATION_DLQ =
            "fiscal.publication.discovered.dlq";

    public static final String PUBLICATION_DLQ_ROUTING_KEY =
            "publication.discovered.dlq";
}