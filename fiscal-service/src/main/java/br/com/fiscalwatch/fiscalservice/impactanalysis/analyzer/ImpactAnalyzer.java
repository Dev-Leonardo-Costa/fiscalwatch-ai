package br.com.fiscalwatch.fiscalservice.impactanalysis.analyzer;

public interface ImpactAnalyzer {

    ImpactAnalysisResult analyze(PublicationAnalysisInput publication);
}
