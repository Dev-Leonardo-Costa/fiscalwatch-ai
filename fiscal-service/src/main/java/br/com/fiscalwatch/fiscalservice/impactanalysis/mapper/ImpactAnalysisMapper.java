package br.com.fiscalwatch.fiscalservice.impactanalysis.mapper;

import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ActionItemResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.EvidenceResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.ImpactAnalysisResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.dto.TechnicalImpactResponse;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ActionItem;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.Evidence;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.ImpactAnalysis;
import br.com.fiscalwatch.fiscalservice.impactanalysis.entity.TechnicalImpact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImpactAnalysisMapper {

    @Mapping(target = "publicationId", source = "publication.id")
    ImpactAnalysisResponse toResponse(ImpactAnalysis impactAnalysis);

    TechnicalImpactResponse toResponse(TechnicalImpact technicalImpact);

    ActionItemResponse toResponse(ActionItem actionItem);

    EvidenceResponse toResponse(Evidence evidence);
}
