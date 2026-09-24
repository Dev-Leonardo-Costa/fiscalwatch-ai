from datetime import datetime

from app.model.publication import Publication
from datetime import datetime, timedelta

from app.service.publication_service import (
    remove_duplicate_publications,
    is_relevant_publication,
    filter_recent_publications
)

def test_deve_remover_publicacao_com_external_id_duplicado():
    external_id = "a" * 64

    primeira_publicacao = Publication(
        external_id=external_id,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now()
    )

    segunda_publicacao = Publication(
        external_id=external_id,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now()
    )

    resultado = remove_duplicate_publications([
        primeira_publicacao,
        segunda_publicacao
    ])

    assert len(resultado) == 1
    assert resultado[0].external_id == external_id


def test_deve_manter_publicacoes_com_external_ids_diferentes():
    primeira_publicacao = Publication(
        external_id="a" * 64,
        source="SVRS",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now()
    )

    segunda_publicacao = Publication(
        external_id="b" * 64,
        source="PORTAL_NFE",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now()
    )

    resultado = remove_duplicate_publications([
        primeira_publicacao,
        segunda_publicacao
    ])

    assert len(resultado) == 2


def test_deve_considerar_publicacao_fiscal_relevante():
    publicacao = Publication(
        external_id="c" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title="ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS Nº 4",
        document_type="ATO_TECNICO",
        published_at=datetime.now(),
        description="Documentação técnica aplicável à CBS e ao IBS."
    )

    resultado = is_relevant_publication(publicacao)

    assert resultado is True   


def test_nao_deve_considerar_sigla_ibs_sem_contexto_fiscal_como_relevante():
    publicacao = Publication(
        external_id="d" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title="PORTARIA SEFIC/MINC Nº 628",
        document_type="OUTRO",
        published_at=datetime.now(),
        description="Plano Bianual de Atividades Brasil Solidário - INSTITUTO BRASIL SOLIDARIO - IBS"
    )

    resultado = is_relevant_publication(publicacao)

    assert resultado is False     


def test_deve_manter_apenas_publicacoes_das_ultimas_72_horas():
    agora = datetime.now()

    publicacao_recente = Publication(
        external_id="e" * 64,
        source="SVRS",
        title="Nota Técnica recente",
        document_type="NOTA_TECNICA",
        published_at=agora - timedelta(hours=24)
    )

    publicacao_antiga = Publication(
        external_id="f" * 64,
        source="SVRS",
        title="Nota Técnica antiga",
        document_type="NOTA_TECNICA",
        published_at=agora - timedelta(hours=96)
    )

    resultado = filter_recent_publications(
        [publicacao_recente, publicacao_antiga],
        hours=72
    )

    assert len(resultado) == 1
    assert resultado[0].external_id == publicacao_recente.external_id    