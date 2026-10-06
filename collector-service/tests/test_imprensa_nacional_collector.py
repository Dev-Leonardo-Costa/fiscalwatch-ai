from datetime import datetime

from app.collectors import imprensa_nacional_collector


def test_search_dou_deve_retornar_html_da_busca(monkeypatch):
    html = "<html><body>Resultado DOU</body></html>"
    chamadas = []

    class RespostaHttp:
        text = html

        def raise_for_status(self):
            chamadas.append("raise_for_status")

    def fake_get(url, params, headers, timeout, follow_redirects):
        chamadas.append({
            "url": url,
            "params": params,
            "headers": headers,
            "timeout": timeout,
            "follow_redirects": follow_redirects
        })
        return RespostaHttp()

    monkeypatch.setattr(
        imprensa_nacional_collector.httpx,
        "get",
        fake_get
    )

    resultado = imprensa_nacional_collector.search_dou(
        keyword="IBS",
        section="do1"
    )

    assert resultado == html
    assert chamadas[0]["url"] == imprensa_nacional_collector.DOU_SEARCH_URL
    assert chamadas[0]["params"] == {
        "q": "IBS",
        "s": "do1",
        "exactDate": "all"
    }
    assert chamadas[0]["timeout"] == 30.0
    assert chamadas[0]["follow_redirects"] is True
    assert "User-Agent" in chamadas[0]["headers"]
    assert chamadas[1] == "raise_for_status"


def test_parse_dou_search_results_deve_extrair_json_array():
    html = """
    <html>
        <body>
            <script
                id="_br_com_seatecnologia_in_buscadou_BuscaDouPortlet_params"
                type="application/json"
            >
                {
                    "jsonArray": [
                        {
                            "title": "ATO TÉCNICO CONJUNTO",
                            "artType": "Ato",
                            "pubDate": "08/09/2026",
                            "content": "Documentação técnica CBS e IBS",
                            "urlTitle": "ato-tecnico-conjunto"
                        }
                    ]
                }
            </script>
        </body>
    </html>
    """

    resultados = imprensa_nacional_collector.parse_dou_search_results(html)

    assert resultados == [
        {
            "title": "ATO TÉCNICO CONJUNTO",
            "artType": "Ato",
            "pubDate": "08/09/2026",
            "content": "Documentação técnica CBS e IBS",
            "urlTitle": "ato-tecnico-conjunto"
        }
    ]


def test_parse_dou_search_results_deve_retornar_lista_vazia_sem_elemento():
    html = "<html><body>Sem params do DOU</body></html>"

    resultados = imprensa_nacional_collector.parse_dou_search_results(html)

    assert resultados == []


def test_parse_dou_publications_deve_montar_publication_do_dou():
    resultados = [
        {
            "title": (
                "ATO TÉCNICO CONJUNTO RFB/SUARA/CGIBS Nº 4, "
                "DE 28 DE AGOSTO DE 2026"
            ),
            "artType": "Ato",
            "pubDate": "08/09/2026",
            "content": "Documentação técnica aplicável à CBS e ao IBS.",
            "urlTitle": (
                "ato-tecnico-conjunto-rfb/suara/cgibs/"
                "diretoria-executiva-n-4-de-28-de-agosto-de-2026-730303692"
            )
        }
    ]

    publicacoes = imprensa_nacional_collector.parse_dou_publications(
        resultados
    )

    assert len(publicacoes) == 1
    publicacao = publicacoes[0]
    assert publicacao.source == "IMPRENSA_NACIONAL_DOU"
    assert publicacao.external_id
    assert len(publicacao.external_id) == 64
    assert publicacao.external_id == (
        imprensa_nacional_collector.generate_external_id(
            source="IMPRENSA_NACIONAL_DOU",
            source_identifier=resultados[0]["urlTitle"]
        )
    )
    assert publicacao.published_at == datetime(2026, 9, 8)
    assert publicacao.modified_at is None
    assert publicacao.download_url == (
        "https://www.in.gov.br/web/dou/-/"
        "ato-tecnico-conjunto-rfb/suara/cgibs/"
        "diretoria-executiva-n-4-de-28-de-agosto-de-2026-730303692"
    )
    assert publicacao.description == (
        "Documentação técnica aplicável à CBS e ao IBS."
    )
    assert publicacao.document_type == "ATO_TECNICO"


def test_parse_dou_publications_deve_gerar_external_id_deterministico():
    primeiro_resultado = {
        "title": "ATO TÉCNICO CONJUNTO",
        "artType": "Ato",
        "pubDate": "08/09/2026",
        "content": "Documentação técnica CBS e IBS",
        "urlTitle": "ato-tecnico-conjunto"
    }
    segundo_resultado = {
        "title": "ATO TÉCNICO CONJUNTO",
        "artType": "Ato",
        "pubDate": "08/09/2026",
        "content": "Documentação técnica CBS e IBS",
        "urlTitle": "ato-tecnico-conjunto"
    }
    resultado_diferente = {
        "title": "PORTARIA CONJUNTA MF/CGIBS",
        "artType": "Portaria Conjunta",
        "pubDate": "05/10/2026",
        "content": "Metodologia de cálculo do IBS",
        "urlTitle": "portaria-conjunta-mf-cgibs"
    }

    primeira_publicacao = imprensa_nacional_collector.parse_dou_publications(
        [primeiro_resultado]
    )[0]
    segunda_publicacao = imprensa_nacional_collector.parse_dou_publications(
        [segundo_resultado]
    )[0]
    publicacao_diferente = (
        imprensa_nacional_collector.parse_dou_publications(
            [resultado_diferente]
        )[0]
    )

    assert primeira_publicacao.external_id == segunda_publicacao.external_id
    assert primeira_publicacao.external_id != publicacao_diferente.external_id


def test_get_imprensa_nacional_publications_deve_retornar_publicacoes(
    monkeypatch
):
    html = "<html>resultado dou</html>"
    resultados = [
        {
            "title": "ATO TÉCNICO CONJUNTO",
            "artType": "Ato",
            "pubDate": "08/09/2026",
            "content": "Documentação técnica CBS e IBS",
            "urlTitle": "ato-tecnico-conjunto"
        }
    ]
    chamadas = []

    def fake_search_dou(keyword, section):
        chamadas.append((keyword, section))
        return html

    monkeypatch.setattr(
        imprensa_nacional_collector,
        "search_dou",
        fake_search_dou
    )
    monkeypatch.setattr(
        imprensa_nacional_collector,
        "parse_dou_search_results",
        lambda received_html: resultados
    )

    publicacoes = (
        imprensa_nacional_collector.get_imprensa_nacional_publications()
    )

    assert chamadas == [("IBS", "do1")]
    assert len(publicacoes) == 1
    assert publicacoes[0].source == "IMPRENSA_NACIONAL_DOU"
    assert publicacoes[0].title == "ATO TÉCNICO CONJUNTO"


def test_get_imprensa_nacional_publications_deve_retornar_lista_vazia(
    monkeypatch
):
    monkeypatch.setattr(
        imprensa_nacional_collector,
        "search_dou",
        lambda keyword, section: "<html>sem resultados</html>"
    )
    monkeypatch.setattr(
        imprensa_nacional_collector,
        "parse_dou_search_results",
        lambda html: []
    )

    publicacoes = (
        imprensa_nacional_collector.get_imprensa_nacional_publications()
    )

    assert publicacoes == []
