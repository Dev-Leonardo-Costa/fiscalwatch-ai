package br.com.fiscalwatch.fiscalservice.publication.exception;

public class PublicationAlreadyExistsException extends RuntimeException {

    public PublicationAlreadyExistsException(String externalId) {
        super("Publicação já cadastrada com externalId: " + externalId);
    }
}