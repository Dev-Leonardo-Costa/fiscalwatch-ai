package br.com.fiscalwatch.fiscalservice.publication.exception;

public class PublicationNotFoundException extends RuntimeException {

    public PublicationNotFoundException(Long id) {
        super("Publicação não encontrada com id:" + id);
    }

    public PublicationNotFoundException(String externalId) {
        super("Publicação não encontrada com externalId:" + externalId);
    }
}
