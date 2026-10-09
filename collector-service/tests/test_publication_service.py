from datetime import datetime

import pytest

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


def test_deve_considerar_comite_gestor_ibs_como_contexto_fiscal_relevante():
    publicacao = Publication(
        external_id="g" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title="PORTARIA STN/MF Nº 1.986",
        document_type="OUTRO",
        published_at=datetime.now(),
        description=(
            "Altera procedimentos contábeis relacionados ao "
            "Comitê Gestor IBS e à arrecadação do imposto."
        )
    )

    resultado = is_relevant_publication(publicacao)

    assert resultado is True


def test_nao_deve_considerar_ibs_comercializadora_como_relevante():
    publicacao = Publication(
        external_id="h" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title="Ata de reunião ordinária",
        document_type="OUTRO",
        published_at=datetime.now(),
        description="IBS Comercializadora Ltda."
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


@pytest.mark.parametrize("sigla", ["IBS", "ibs", "Ibs", "iBs"])
@pytest.mark.parametrize("campo", ["title", "description"])
def test_deve_reconhecer_ibs_independente_com_contexto_tributario(sigla, campo):
    campos = {"title": "Comunicado", "description": None}
    campos[campo] = f"Alteração de alíquota do {sigla}"
    publicacao = Publication(
        external_id="i" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        document_type="OUTRO",
        published_at=datetime.now(),
        **campos
    )

    assert is_relevant_publication(publicacao) is True


@pytest.mark.parametrize("texto", [
    "Alíquota do (IBS).",
    "IBS: apuração do imposto",
    "Arrecadação do IBS/2026",
    "Alteracao de aliquota do IBS",
    "Tributação do IBS",
])
def test_deve_reconhecer_ibs_delimitado_por_pontuacao(texto):
    publicacao = Publication(
        external_id="j" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title=texto,
        document_type="OUTRO",
        published_at=datetime.now()
    )

    assert is_relevant_publication(publicacao) is True


@pytest.mark.parametrize("sigla", [
    "IBSPLUS", "PREIBS", "TRIBS", "IBS2", "2IBS", "IBS_ERP", "ÁIBS"
])
@pytest.mark.parametrize("campo", ["title", "description"])
def test_nao_deve_reconhecer_ibs_dentro_de_palavra_maior(sigla, campo):
    campos = {"title": "Comunicado", "description": None}
    campos[campo] = f"Alteração de alíquota do {sigla}"
    publicacao = Publication(
        external_id="k" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        document_type="OUTRO",
        published_at=datetime.now(),
        **campos
    )

    assert is_relevant_publication(publicacao) is False


@pytest.mark.parametrize("texto", [
    "Atualização da CBS",
    "Atualização da NF-e",
    "Atualização da NFC-e",
    "Reforma Tributária",
])
def test_deve_preservar_termos_fiscais_existentes(texto):
    publicacao = Publication(
        external_id="l" * 64,
        source="IMPRENSA_NACIONAL_DOU",
        title=texto,
        document_type="OUTRO",
        published_at=datetime.now()
    )

    assert is_relevant_publication(publicacao) is True
