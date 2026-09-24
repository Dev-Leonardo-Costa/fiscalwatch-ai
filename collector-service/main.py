from fastapi import FastAPI
from app.collectors.receita_collector import fetch_receita_page
from app.collectors.receita_collector import inspect_news_page
from app.collectors.cgibs_collector import fetch_cgibs_page
from app.collectors.nfe_collector import fetch_nfe_portal_page

from app.collectors.cgibs_collector import get_cgibs_technical_publications
from app.collectors.receita_collector import parse_news_publication
from app.collectors.receita_collector import get_news_publications
from app.service.publication_service import filter_recent_publications
from app.collectors.svrs_collector import get_publications
from app.model.publication import Publication
from app.service.publication_identity_service import generate_external_id

from datetime import datetime


from app.service.publication_service import (
    filter_recent_publications,
    merge_publications,
    remove_duplicate_publications,
    find_duplicate_publications,
    is_relevant_publication,
    filter_relevant_publications
)

from app.collectors.imprensa_nacional_collector import (
    search_dou,
    parse_dou_search_results,
    parse_dou_publications
)


from app.collectors.svrs_collector import (
    fetch_svrs_page,
    parse_svrs_page,
    inspect_document,
    find_download_script,
    build_download_url,
    inspect_publication_container,
    get_publications,
    download_document,
    extract_pdf_text,
    normalize_text,
)


from app.collectors.imprensa_nacional_collector import (
    fetch_imprensa_nacional_page,
    parse_imprensa_links,
    fetch_dou_page,
    fetch_dou_database_page,
    parse_dou_database_content,
    fetch_dou_section_page,
)


from app.collectors.cgibs_collector import (
    fetch_cgibs_page,
    parse_cgibs_links,
    fetch_cgibs_technical_documents_page,
    inspect_technical_documents_page,
    fetch_cgibs_technical_documents_api,
    inspect_cgibs_scripts,
    fetch_cgibs_paged_list_js,
    get_cgibs_script_sources,
    fetch_cgibs_matriz_ui_js,
    parse_cgibs_technical_documents,
    inspect_cgibs_document_page,
    parse_cgibs_technical_files,
    inspect_cgibs_dere_dates,
    inspect_cgibs_dere_metadata,
    parse_cgibs_document_metadata,
    parse_cgibs_document_publication,
    get_cgibs_technical_publications
)

from app.collectors.receita_collector import (
    fetch_receita_page,
    parse_receita_links,
    filter_relevant_links,
    filter_news_links,
)


from app.collectors.imprensa_nacional_collector import (
    fetch_imprensa_nacional_page,
    parse_imprensa_links,
    fetch_dou_page,
    fetch_dou_database_page,
    parse_dou_database_content,
    fetch_dou_section_page,
    parse_dou_section_links,
    find_dou_api_candidates,
    search_dou,
    extract_dou_search_params,
    parse_dou_search_results,
    parse_dou_publications
)


from app.collectors.nfe_collector import (
    fetch_nfe_notas_tecnicas_page,
    parse_nfe_notas_tecnicas_links,
    parse_nfe_publications
)


from app.model.publication_event import (
    PublicationEvent,
    PUBLICATION_DISCOVERED
)


app = FastAPI(
    title="FiscalWatch Collector Service",
    version="0.1.0"
)


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "collector-service"
    }


@app.get("/test/svrs")
def test_svrs():
    html = fetch_svrs_page()
    links = parse_svrs_page(html)

    return {
        "source": "SVRS",
        "status": "SUCCESS",
        "total_links": len(links),
        "links": links[:30]
    }


@app.get("/test/svrs/document")
def test_svrs_document():
    html = fetch_svrs_page()

    document = inspect_document(
        html,
        "Evento 211110 - NT 2025.002 v1.40"
    )

    return {
        "source": "SVRS",
        "document": document
    }


@app.get("/test/svrs/download-script")
def test_download_script():
    html = fetch_svrs_page()

    script = find_download_script(html)

    return {
        "source": "SVRS",
        "script": script
    }


@app.get("/test/svrs/download-url")
def test_download_url():
    onclick = (
        "download_arquivo_estatico("
        "'NFE', 2, "
        "'Schema_Evento_211110_NT2025.002 v1.40.zip');"
    )

    url = build_download_url(onclick)

    return {
        "download_url": url
    }


@app.get("/test/svrs/publication")
def test_svrs_publication():
    html = fetch_svrs_page()

    publication = inspect_publication_container(
        html,
        "Evento 211110 - NT 2025.002 v1.40"
    )

    return {
        "source": "SVRS",
        "html": publication
    }


@app.get("/publications")
def publications():
    return get_publications()


@app.get("/publications/recent")
def recent_publications(hours: int = 72):

    publications = get_publications()

    recent = filter_recent_publications(
        publications,
        hours
    )

    return {
        "source": "SVRS",
        "period_hours": hours,
        "total": len(recent),
        "publications": recent
    }


@app.get("/test/svrs/download")
def test_download():

    publications = get_publications()

    publication = next(
        (
            publication
            for publication in publications
            if publication.title == "Nota Técnica 2026.009 v.1.00"
        ),
        None
    )

    if publication is None:
        return {
            "status": "NOT_FOUND"
        }

    if not publication.download_url:
        return {
            "status": "NO_DOWNLOAD_URL"
        }

    file_path = download_document(
        publication.download_url,
        "NT2026.009_v1.00.pdf"
    )

    return {
        "status": "SUCCESS",
        "title": publication.title,
        "file": file_path
    }


@app.get("/test/svrs/pdf-text")
def test_pdf_text():

    file_path = "downloads/NT2026.009_v1.00.pdf"

    raw_text = extract_pdf_text(file_path)

    normalized_text = normalize_text(raw_text)

    return {
        "raw_characters": len(raw_text),
        "normalized_characters": len(normalized_text),
        "preview": normalized_text[:2000]
    }


@app.get("/test/receita")
def test_receita():
    html = fetch_receita_page()

    return {
        "source": "RECEITA_FEDERAL",
        "status": "SUCCESS",
        "html_size": len(html)
    }


@app.get("/test/receita/links")
def test_receita_links():
    html = fetch_receita_page()

    links = parse_receita_links(html)

    return {
        "source": "RECEITA_FEDERAL",
        "total_links": len(links),
        "links": links
    }


@app.get("/test/receita/relevant-links")
def test_receita_relevant_links():

    html = fetch_receita_page()

    links = parse_receita_links(html)

    relevant_links = filter_relevant_links(links)

    return {
        "source": "RECEITA_FEDERAL",
        "total_links": len(links),
        "relevant_links": len(relevant_links),
        "links": relevant_links
    }


@app.get("/test/receita/news")
def test_receita_news():

    html = fetch_receita_page()

    links = parse_receita_links(html)
    relevant_links = filter_relevant_links(links)
    news_links = filter_news_links(relevant_links)

    return {
        "source": "RECEITA_FEDERAL",
        "total": len(news_links),
        "publications": news_links
    }


@app.get("/test/receita/news/inspect")
def test_receita_news_inspect():

    url = (
        "https://www.gov.br/receitafederal/pt-br/"
        "assuntos/noticias/2026/setembro/"
        "receita-federal-publica-nova-documentacao-"
        "tecnica-das-apis-de-apuracao-de-cbs"
    )

    return inspect_news_page(url)


@app.get("/test/receita/publication")
def test_receita_publication():

    url = (
        "https://www.gov.br/receitafederal/pt-br/"
        "assuntos/noticias/2026/setembro/"
        "receita-federal-publica-nova-documentacao-"
        "tecnica-das-apis-de-apuracao-de-cbs"
    )

    return parse_news_publication(url)


@app.get("/test/receita/publications")
def test_receita_publications():

    publications = get_news_publications()

    return {
        "source": "RECEITA_FEDERAL",
        "total": len(publications),
        "publications": publications
    }


@app.get("/test/receita/recent")
def test_receita_recent(hours: int = 72):

    publications = get_news_publications()

    recent = filter_recent_publications(
        publications,
        hours
    )

    return {
        "source": "RECEITA_FEDERAL",
        "period_hours": hours,
        "total": len(recent),
        "publications": recent
    }


@app.get("/test/cgibs")
def test_cgibs():

    html = fetch_cgibs_page()

    return {
        "source": "CGIBS",
        "status": "SUCCESS",
        "html_size": len(html)
    }


@app.get("/test/cgibs/links")
def test_cgibs_links():

    html = fetch_cgibs_page()

    links = parse_cgibs_links(html)

    return {
        "source": "CGIBS",
        "total_links": len(links),
        "links": links
    }


@app.get("/test/cgibs/technical-documents")
def test_cgibs_technical_documents():

    html = fetch_cgibs_technical_documents_page()

    links = parse_cgibs_links(html)

    return {
        "source": "CGIBS",
        "section": "DOCUMENTOS_TECNICOS",
        "html_size": len(html),
        "total_links": len(links),
        "links": links
    }


@app.get("/test/cgibs/technical-documents/inspect")
def test_cgibs_technical_documents_inspect():

    html = fetch_cgibs_technical_documents_page()

    result = inspect_technical_documents_page(html)

    return {
        "source": "CGIBS",
        "section": "DOCUMENTOS_TECNICOS",
        **result
    }


@app.get("/test/cgibs/technical-documents/api")
def test_cgibs_technical_documents_api():

    data = fetch_cgibs_technical_documents_api()

    return {
        "source": "CGIBS",
        "status": "SUCCESS",
        "recordcount": data.get("recordcount"),
        "pagecount": data.get("pagecount"),
        "body": data.get("body")
    }


@app.get("/test/cgibs/scripts")
def test_cgibs_scripts():

    html = fetch_cgibs_technical_documents_page()

    scripts = inspect_cgibs_scripts(html)

    return {
        "source": "CGIBS",
        "total": len(scripts),
        "scripts": scripts
    }


@app.get("/test/cgibs/pagedlist-js")
def test_cgibs_pagedlist_js():

    content = fetch_cgibs_paged_list_js()

    return {
        "size": len(content),
        "content": content
    }


@app.get("/test/cgibs/all-scripts")
def test_cgibs_all_scripts():

    html = fetch_cgibs_technical_documents_page()

    scripts = get_cgibs_script_sources(html)

    return {
        "total": len(scripts),
        "scripts": scripts
    }


@app.get("/test/cgibs/matriz-ui-js")
def test_cgibs_matriz_ui_js():

    content = fetch_cgibs_matriz_ui_js()

    return {
        "size": len(content),
        "contains_list_create": "list.create" in content,
        "contains_filtered_list": "filtered-list" in content,
        "contains_source_uri": "source-uri" in content,
        "content": content
    }


@app.get("/test/cgibs/technical-documents/parsed")
def test_cgibs_technical_documents_parsed():

    data = fetch_cgibs_technical_documents_api()

    documents = parse_cgibs_technical_documents(
        data.get("body", "")
    )

    return {
        "source": "CGIBS",
        "total": len(documents),
        "documents": documents
    }


@app.get("/test/cgibs/dere")
def test_cgibs_dere():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    return inspect_cgibs_document_page(url)


@app.get("/test/cgibs/dere/files")
def test_cgibs_dere_files():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    files = parse_cgibs_technical_files(url)

    return {
        "source": "CGIBS",
        "document": "DeRE",
        "total": len(files),
        "files": files
    }


@app.get("/test/cgibs/dere/dates")
def test_cgibs_dere_dates():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    dates = inspect_cgibs_dere_dates(url)

    return {
        "source": "CGIBS",
        "document": "DeRE",
        "total": len(dates),
        "dates": dates
    }


@app.get("/test/cgibs/dere/metadata")
def test_cgibs_dere_metadata():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    metadata = inspect_cgibs_dere_metadata(url)

    return {
        "source": "CGIBS",
        "document": "DeRE",
        "total": len(metadata),
        "metadata": metadata
    }


@app.get("/test/cgibs/dere/metadata/parsed")
def test_cgibs_dere_metadata_parsed():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    return parse_cgibs_document_metadata(url)


@app.get("/test/cgibs/dere/publication")
def test_cgibs_dere_publication():

    url = (
        "https://www.cgibs.gov.br/"
        "declaracao-de-regimes-especificos-dere"
    )

    return parse_cgibs_document_publication(url)


@app.get("/test/cgibs/publications")
def test_cgibs_publications():

    publications = get_cgibs_technical_publications()

    return {
        "source": "CGIBS",
        "total": len(publications),
        "publications": publications
    }


@app.get("/test/cgibs/recent")
def test_cgibs_recent(hours: int = 72):

    publications = get_cgibs_technical_publications()

    recent_publications = filter_recent_publications(
        publications,
        hours
    )

    return {
        "source": "CGIBS",
        "period_hours": hours,
        "total": len(recent_publications),
        "publications": recent_publications
    }


@app.get("/test/imprensa-nacional/dou")
def test_imprensa_nacional_dou():

    html = fetch_dou_page()

    links = parse_imprensa_links(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "html_size": len(html),
        "total_links": len(links),
        "links": links
    }


@app.get("/test/imprensa-nacional")
def test_imprensa_nacional():

    html = fetch_imprensa_nacional_page()

    return {
        "source": "IMPRENSA_NACIONAL",
        "status": "SUCCESS",
        "html_size": len(html)
    }


@app.get("/test/imprensa-nacional/links")
def test_imprensa_nacional_links():

    html = fetch_imprensa_nacional_page()

    links = parse_imprensa_links(html)

    return {
        "source": "IMPRENSA_NACIONAL",
        "total": len(links),
        "links": links
    }


@app.get("/test/imprensa-nacional/dou")
def test_imprensa_nacional_dou():

    html = fetch_dou_page()

    links = parse_imprensa_links(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "html_size": len(html),
        "total_links": len(links),
        "links": links
    }


@app.get("/test/imprensa-nacional/dou/database")
def test_dou_database():

    html = fetch_dou_database_page()

    links = parse_imprensa_links(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU_DATABASE",
        "html_size": len(html),
        "total_links": len(links),
        "links": links
    }


@app.get("/test/imprensa-nacional/dou/database/content")
def test_dou_database_content():

    html = fetch_dou_database_page()

    content = parse_dou_database_content(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU_DATABASE",
        "content": content
    }


@app.get("/test/imprensa-nacional/dou/section")
def test_dou_section():

    html = fetch_dou_section_page(
        date="19-09-2026"
    )

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "section": "dou1",
        "date": "19-09-2026",
        "html_size": len(html)
    }


@app.get("/test/imprensa-nacional/dou/section/links")
def test_dou_section_links():

    html = fetch_dou_section_page(
        date="19-09-2026"
    )

    links = parse_dou_section_links(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "section": "dou1",
        "date": "19-09-2026",
        "total_links": len(links),
        "links": links
    }


@app.get("/test/imprensa-nacional/dou/api-candidates")
def test_dou_api_candidates():

    html = fetch_dou_section_page(
        date="19-09-2026"
    )

    candidates = find_dou_api_candidates(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "total": len(candidates),
        "candidates": candidates
    }


@app.get("/test/imprensa-nacional/dou/search")
def test_dou_search():

    html = search_dou(
        keyword="IBS",
        section="do1"
    )

    params = extract_dou_search_params(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "keyword": "IBS",
        "found": params is not None,
        "params_size": len(params) if params else 0,
        "preview": params[:3000] if params else None
    }


@app.get("/test/imprensa-nacional/dou/search/params")
def test_dou_search_params():

    html = search_dou(
        date="19-09-2026",
        section="do1"
    )

    params = extract_dou_search_params(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "found": params is not None,
        "params_size": len(params) if params else 0,
        "preview": params[:2000] if params else None
    }


@app.get("/test/imprensa-nacional/dou/section/params")
def test_dou_section_params():

    html = fetch_dou_section_page(
        date="19-09-2026",
        section="dou1"
    )

    params = extract_dou_search_params(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "found": params is not None,
        "params_size": len(params) if params else 0,
        "preview": params[:3000] if params else None
    }


@app.get("/test/imprensa-nacional/dou/results")
def test_dou_results():

    html = search_dou(
        keyword="IBS",
        section="do1"
    )

    results = parse_dou_search_results(html)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "keyword": "IBS",
        "total": len(results),
        "results": results
    }


@app.get("/test/imprensa-nacional/dou/publications")
def test_dou_publications():

    html = search_dou(
        keyword="IBS",
        section="do1"
    )

    results = parse_dou_search_results(html)

    publications = parse_dou_publications(results)

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "total": len(publications),
        "publications": publications
    }


@app.get("/test/imprensa-nacional/dou/recent")
def test_dou_recent(hours: int = 72):

    html = search_dou(
        keyword="IBS",
        section="do1"
    )

    results = parse_dou_search_results(html)

    publications = parse_dou_publications(results)

    recent_publications = filter_recent_publications(
        publications,
        hours
    )

    return {
        "source": "IMPRENSA_NACIONAL_DOU",
        "keyword": "IBS",
        "hours": hours,
        "total": len(recent_publications),
        "publications": recent_publications
    }


@app.get("/test/nfe")
def test_nfe():

    html = fetch_nfe_portal_page()

    return {
        "source": "PORTAL_NFE",
        "status": "SUCCESS",
        "html_size": len(html),
        "preview": html
    }


@app.get("/test/nfe/notas-tecnicas")
def test_nfe_notas_tecnicas():

    html = fetch_nfe_notas_tecnicas_page()

    notas = parse_nfe_notas_tecnicas_links(html)

    publications = parse_nfe_publications(notas)

    return {
        "source": "PORTAL_NFE",
        "total": len(publications),
        "publications": publications[:10]
    }


@app.get("/test/publications/merge/nfe")
def test_merge_nfe(hours: int = 72):

    # Portal NF-e
    nfe_html = fetch_nfe_notas_tecnicas_page()
    notas = parse_nfe_notas_tecnicas_links(nfe_html)
    nfe_publications = parse_nfe_publications(notas)

    # Receita Federal
    receita_publications = get_news_publications()

    # CGIBS
    cgibs_publications = get_cgibs_technical_publications()

    # SVRS
    svrs_publications = get_publications()

    # Diário Oficial da União
    dou_html = search_dou(
        keyword="IBS",
        section="do1"
    )

    dou_results = parse_dou_search_results(dou_html)
    dou_publications = parse_dou_publications(dou_results)

    # Unificação das 5 fontes
    publications = merge_publications(
        nfe_publications,
        receita_publications,
        cgibs_publications,
        svrs_publications,
        dou_publications
    )

    duplicates = find_duplicate_publications(
        publications
    )

    # Remove duplicações dentro da mesma fonte
    unique_publications = remove_duplicate_publications(
        publications
    )

    # Filtra pelo período informado
    recent_publications = filter_recent_publications(
        unique_publications,
        hours=hours
    )

    relevant_publications = filter_relevant_publications(
        recent_publications
    )

    return {
        "total_collected": len(publications),
        "total_unique": len(unique_publications),
        "total_duplicates": len(duplicates),
        "total_recent": len(recent_publications),
        "total_relevant": len(relevant_publications),
        "relevant_publications": relevant_publications,
        "hours": hours,
        "duplicates": duplicates,
        "sources": {
            "PORTAL_NFE": len(nfe_publications),
            "RECEITA_FEDERAL": len(receita_publications),
            "CGIBS": len(cgibs_publications),
            "SVRS": len(svrs_publications),
            "IMPRENSA_NACIONAL_DOU": len(dou_publications)
        },
        "publications": recent_publications
    }


@app.get("/test/publications/deduplicate")
def test_deduplicate_publications():

    svrs_external_id = generate_external_id(
        source="SVRS",
        source_identifier="NT2026.009_v1.00"
    )

    portal_nfe_external_id = generate_external_id(
        source="PORTAL_NFE",
        source_identifier="NT2026.009_v1.00"
    )

    publications = [
        Publication(
            external_id=svrs_external_id,
            source="SVRS",
            title="Nota Técnica 2026.009 v1.00",
            document_type="NOTA_TECNICA",
            published_at=datetime.now()
        ),
        Publication(
            external_id=svrs_external_id,
            source="SVRS",
            title="Nota Técnica 2026.009 v1.00",
            document_type="NOTA_TECNICA",
            published_at=datetime.now()
        ),
        Publication(
            external_id=portal_nfe_external_id,
            source="PORTAL_NFE",
            title="Nota Técnica 2026.009 v1.00",
            document_type="NOTA_TECNICA",
            published_at=datetime.now()
        )
    ]

    unique_publications = remove_duplicate_publications(
        publications
    )

    return {
        "before": len(publications),
        "after": len(unique_publications),
        "publications": unique_publications
    }


@app.get("/test/publications/relevance")
def test_publication_relevance():

    relevant = Publication(
        source="TESTE",
        title="Nota Técnica sobre IBS e CBS",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        description="Alteração em regra de validação da NF-e."
    )

    irrelevant = Publication(
        source="TESTE",
        title="PAUTA DE JULGAMENTO",
        document_type="PAUTA",
        published_at=datetime.now(),
        description="Reunião para julgamento de processos administrativos."
    )

    return {
        "relevant": is_relevant_publication(relevant),
        "irrelevant": is_relevant_publication(irrelevant)
    }


@app.get("/test/publication-event")
def test_publication_event():

    external_id = generate_external_id(
        source="PORTAL_NFE",
        source_identifier="https://www.nfe.fazenda.gov.br/test/nota-tecnica-2026-009"
    )

    publication = Publication(
        external_id=external_id,
        source="PORTAL_NFE",
        title="Nota Técnica 2026.009 v1.00",
        document_type="NOTA_TECNICA",
        published_at=datetime.now(),
        description="Alteração de regra de validação de CFOP",
        download_url="https://www.nfe.fazenda.gov.br/"
    )

    event = PublicationEvent(
        event_type=PUBLICATION_DISCOVERED,
        occurred_at=datetime.now(),
        publication=publication
    )

    return event