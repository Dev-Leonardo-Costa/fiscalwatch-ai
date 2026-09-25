package br.com.fiscalwatch.fiscalservice.publication.messaging;

import br.com.fiscalwatch.fiscalservice.publication.dto.PublicationRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PublicationEvent(

        @JsonProperty("event_type")
        String eventType,

        @JsonProperty("occurred_at")
        LocalDateTime occurredAt,

        PublicationRequest publication
) {
}