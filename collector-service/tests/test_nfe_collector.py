from datetime import datetime

from app.collectors import nfe_collector


def test_fetch_nfe_portal_page_deve_retornar_html_da_resposta(monkeypatch):
    html = "<html><body>Portal NF-e</body></html>"
    chamadas = []

    class RespostaHttp:
        text = html

        def raise_for_status(self):
            chamadas.append("raise_for_status")

    def fake_get(url, headers, timeout, follow_redirects):
        chamadas.append({
            "url": url,
            "headers": headers,
            "timeout": timeout,
            "follow_redirects": follow_redirects
        })
        return RespostaHttp()

    monkeypatch.setattr(nfe_collector.httpx, "get", fake_get)

    resultado = nfe_collector.fetch_nfe_portal_page()

    assert resultado == html
    assert chamadas[0]["url"] == nfe_collector.NFE_PORTAL_URL
    assert chamadas[0]["timeout"] == 30.0
    assert chamadas[0]["follow_redirects"] is True
    assert "User-Agent" in chamadas[0]["headers"]
    assert chamadas[1] == "raise_for_status"


def test_parse_nfe_notas_tecnicas_links_deve_identificar_notas_tecnicas():
    html = """
    <html>
        <body>
            <a href="exibirArquivo.aspx?conteudo=abc">
                Nota Técnica 2026.001 v1.00 - Publicada em 01/10/2026
            </a>
            <a href="exibirArquivo.aspx?conteudo=def">
                NT 2026.002 v1.00 - Publicada em 02/10/2026
            </a>
            <a href="consulta.aspx">Consulta NF-e</a>
            <a href="webServices.aspx">Web Services</a>
        </body>
    </html>
    """

    notas = nfe_collector.parse_nfe_notas_tecnicas_links(html)

    assert notas == [
        {
            "title": (
                "Nota Técnica 2026.001 v1.00 - "
                "Publicada em 01/10/2026"
            ),
            "href": "exibirArquivo.aspx?conteudo=abc"
        },
        {
            "title": "NT 2026.002 v1.00 - Publicada em 02/10/2026",
            "href": "exibirArquivo.aspx?conteudo=def"
        }
    ]


def test_parse_nfe_notas_tecnicas_links_deve_reconhecer_nbsp():
    html = """
    <html>
        <body>
            <a href="exibirArquivo.aspx?conteudo=nbsp">
                Nota&nbsp;Técnica 2025.001. v.1.03 - Corrigido -
                Publicada em 29/09/2025
            </a>
        </body>
    </html>
    """

    notas = nfe_collector.parse_nfe_notas_tecnicas_links(html)

    assert len(notas) == 1
    assert notas[0]["href"] == "exibirArquivo.aspx?conteudo=nbsp"
    assert "Nota\u00a0Técnica 2025.001. v.1.03" in notas[0]["title"]
    assert "Publicada em 29/09/2025" in notas[0]["title"]


def test_parse_nfe_notas_tecnicas_links_deve_manter_espaco_comum():
    html = """
    <html>
        <body>
            <a href="exibirArquivo.aspx?conteudo=espaco">
                Nota Técnica 2026.001 v1.00 - Publicada em 01/10/2026
            </a>
        </body>
    </html>
    """

    notas = nfe_collector.parse_nfe_notas_tecnicas_links(html)

    assert notas == [
        {
            "title": (
                "Nota Técnica 2026.001 v1.00 - "
                "Publicada em 01/10/2026"
            ),
            "href": "exibirArquivo.aspx?conteudo=espaco"
        }
    ]


def test_parse_nfe_publications_deve_montar_publicacao_de_nota_tecnica():
    notas = [
        {
            "title": (
                "Nota Técnica 2026.001 v1.00 - "
                "Publicada em 01/10/2026"
            ),
            "href": "exibirArquivo.aspx?conteudo=abc"
        }
    ]

    publicacoes = nfe_collector.parse_nfe_publications(notas)

    assert len(publicacoes) == 1
    publicacao = publicacoes[0]
    assert publicacao.source == "PORTAL_NFE"
    assert publicacao.document_type == "NOTA_TECNICA"
    assert publicacao.published_at == datetime(2026, 10, 1)
    assert publicacao.modified_at is None
    assert publicacao.download_url == (
        "https://www.nfe.fazenda.gov.br/portal/"
        "exibirArquivo.aspx?conteudo=abc"
    )
    assert publicacao.external_id
    assert len(publicacao.external_id) == 64
    assert publicacao.external_id == (
        nfe_collector.generate_external_id(
            source="PORTAL_NFE",
            source_identifier=publicacao.download_url
        )
    )


def test_parse_nfe_publications_deve_gerar_mesmo_external_id_para_mesmo_link():
    notas = [
        {
            "title": (
                "Nota Técnica 2026.001 v1.00 - "
                "Publicada em 01/10/2026"
            ),
            "href": "exibirArquivo.aspx?conteudo=abc"
        }
    ]

    primeira_execucao = nfe_collector.parse_nfe_publications(notas)
    segunda_execucao = nfe_collector.parse_nfe_publications(notas)

    assert primeira_execucao[0].external_id == (
        segunda_execucao[0].external_id
    )
