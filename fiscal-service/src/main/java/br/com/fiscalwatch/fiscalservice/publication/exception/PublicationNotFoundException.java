package br.com.fiscalwatch.fiscalservice.publication.exception;

public class PublicationNotFoundException extends RuntimeException {

    public PublicationNotFoundException(Long id) {
        super("Publicação não encontrada com id:" + id);
    }
}
