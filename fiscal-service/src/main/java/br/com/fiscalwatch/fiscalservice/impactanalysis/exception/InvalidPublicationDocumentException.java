package br.com.fiscalwatch.fiscalservice.impactanalysis.exception;

public class InvalidPublicationDocumentException extends RuntimeException {
    public InvalidPublicationDocumentException() {
        super("A análise documental exige documento EXTRACTED com conteúdo íntegro e não vazio");
    }
}
