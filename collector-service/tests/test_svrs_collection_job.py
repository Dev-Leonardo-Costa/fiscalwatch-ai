from datetime import datetime
from unittest.mock import Mock
import threading
import importlib
import pika

import pytest

from app.jobs import svrs_collection as module
from app.jobs.svrs_collection import Settings, SvrsCollectionJob
from app.model.publication import Publication
from app.service.svrs_collection_state_client import CollectionState


def publication(number):
    return Publication(external_id=f"{number:064x}", source="SVRS", title="Fixture",
                       published_at=datetime(2026, 1, 1), download_url="https://example.test/a.pdf")


def state(number, exists=False, status=None, valid=False):
    return CollectionState(externalId=f"{number:064x}", exists=exists,
                           extractionStatus=status, validDocument=valid)


@pytest.fixture
def dependencies(monkeypatch):
    collect = Mock(return_value=[publication(1)])
    history = Mock(return_value=state(1))
    dispatch = Mock(return_value={"published": 1, "document": {"extraction_status": "EXTRACTED"}})
    monkeypatch.setattr(module, "get_publications", collect)
    monkeypatch.setattr(module, "fetch_collection_state", history)
    monkeypatch.setattr(module, "dispatch_publication", dispatch)
    return collect, history, dispatch


def test_desativado_por_padrao_sem_acesso_externo(monkeypatch, dependencies):
    for name in ("ENABLED", "INTERVAL_SECONDS", "BATCH_SIZE", "TIMEOUT_SECONDS"):
        monkeypatch.delenv("SVRS_COLLECTION_" + name, raising=False)
    assert Settings.from_environment() == Settings()
    assert SvrsCollectionJob(Settings()).run_once()["published"] == 0
    for dependency in dependencies:
        dependency.assert_not_called()


@pytest.mark.parametrize("exists,status,valid,expected", [
    (False, None, False, 1), (True, "EXTRACTED", True, 0),
    (True, "FAILED", False, 1), (True, "PENDING", False, 1),
    (True, None, False, 1), (True, "EMPTY", False, 0),
    (True, "EXTRACTED", False, 0),
])
def test_seleciona_apenas_novas_ou_recuperaveis(dependencies, exists, status, valid, expected):
    _, history, dispatch = dependencies
    history.return_value = state(1, exists, status, valid)
    result = SvrsCollectionJob(Settings(enabled=True)).run_once()
    assert result["published"] == expected
    assert dispatch.call_count == expected
    if expected:
        assert dispatch.call_args.kwargs == {"timeout": 30, "analyze_schema": False,
                                             "recover_only": exists}


def test_falha_java_interrompe_sem_despacho(dependencies):
    _, history, dispatch = dependencies
    history.side_effect = TimeoutError("segredo que não deve ser registrado")
    assert SvrsCollectionJob(Settings(enabled=True)).run_once()["failed"] == 1
    dispatch.assert_not_called()


def test_falha_individual_continua_sem_sucesso_falso(dependencies, caplog):
    collect, history, dispatch = dependencies
    collect.return_value = [publication(2), publication(1)]
    history.side_effect = [state(1), state(2)]
    dispatch.side_effect = [TimeoutError("senha-secreta"), dispatch.return_value]
    result = SvrsCollectionJob(Settings(enabled=True)).run_once()
    assert result == {"published": 1, "failed": 1, "checked": 2}
    assert "senha-secreta" not in caplog.text


def test_limita_lote_ordena_e_avanca_cursor(dependencies):
    collect, history, dispatch = dependencies
    collect.return_value = [publication(3), publication(1), publication(2), publication(2)]
    history.side_effect = lambda external_id, **kwargs: state(int(external_id, 16))
    job = SvrsCollectionJob(Settings(enabled=True, batch_size=1))
    for _ in range(3):
        assert job.run_once()["published"] == 1
    assert [call.args[0].external_id for call in dispatch.call_args_list] == [
        publication(i).external_id for i in (1, 2, 3)]


def test_ciclos_repetidos_aguardam_persistencia_sem_reenvio(dependencies):
    _, history, dispatch = dependencies
    job = SvrsCollectionJob(Settings(enabled=True))
    job.run_once()
    assert job.run_once()["published"] == 0
    history.return_value = state(1, True, "EXTRACTED", True)
    assert job.run_once()["published"] == 0
    dispatch.assert_called_once()


def test_recuperacao_nao_confirmada_nao_reenvia(dependencies):
    _, history, dispatch = dependencies
    history.return_value = state(1, True, "FAILED")
    job = SvrsCollectionJob(Settings(enabled=True))
    job.run_once()
    job.run_once()
    dispatch.assert_called_once()


def test_recuperacao_ainda_invalida_pode_tentar_no_proximo_ciclo(dependencies):
    _, history, dispatch = dependencies
    history.return_value = state(1, True, "PENDING")
    dispatch.return_value = {"published": 0}
    job = SvrsCollectionJob(Settings(enabled=True))
    assert job.run_once()["published"] == 0
    assert job.run_once()["published"] == 0
    assert dispatch.call_count == 2


def test_falha_coleta_nao_impede_proximo_ciclo(dependencies):
    collect, _, _ = dependencies
    collect.side_effect = [TimeoutError(), [publication(1)]]
    job = SvrsCollectionJob(Settings(enabled=True))
    assert job.run_once()["failed"] == 1
    assert job.run_once()["published"] == 1


def test_nack_pode_recuperar_no_proximo_ciclo_sem_sucesso_falso(dependencies):
    _, _, dispatch = dependencies
    dispatch.side_effect = [pika.exceptions.NackError([]), dispatch.return_value]
    job = SvrsCollectionJob(Settings(enabled=True))
    assert job.run_once()["failed"] == 1
    assert job.run_once()["published"] == 1


def test_timeout_ambiguo_nao_republica_no_ciclo_seguinte(dependencies):
    _, _, dispatch = dependencies
    dispatch.side_effect = TimeoutError()
    job = SvrsCollectionJob(Settings(enabled=True))
    assert job.run_once()["published"] == 0
    assert job.run_once()["published"] == 0
    dispatch.assert_called_once()


def test_nova_publicacao_failed_confirmada_pode_recuperar(dependencies):
    _, history, dispatch = dependencies
    dispatch.return_value = {"published": 1, "document": {"extraction_status": "FAILED"}}
    job = SvrsCollectionJob(Settings(enabled=True))
    assert job.run_once()["published"] == 1
    history.return_value = state(1, True, "FAILED")
    dispatch.return_value = {"published": 1, "document": {"extraction_status": "EXTRACTED"}}
    assert job.run_once()["published"] == 1
    assert dispatch.call_args.kwargs["recover_only"] is True


def test_nao_sobrepoe_mesmo_entre_objetos_no_processo(dependencies):
    collect, _, _ = dependencies
    started, release = threading.Event(), threading.Event()
    def blocked(**kwargs):
        started.set()
        assert release.wait(3)
        return []
    collect.side_effect = blocked
    first = SvrsCollectionJob(Settings(enabled=True))
    thread = threading.Thread(target=first.run_once)
    thread.start()
    try:
        assert started.wait(3)
        assert SvrsCollectionJob(Settings(enabled=True)).run_once()["checked"] == 0
        collect.assert_called_once()
    finally:
        release.set()
        thread.join(3)


def test_reload_da_api_nao_inicia_agendador(monkeypatch, dependencies):
    monkeypatch.setenv("SVRS_COLLECTION_ENABLED", "true")
    import main
    importlib.reload(main)
    for dependency in dependencies:
        dependency.assert_not_called()


def test_cli_desativada_nao_cria_executor(monkeypatch, dependencies):
    monkeypatch.setattr("sys.argv", ["svrs_collection", "--once"])
    monkeypatch.setattr(Settings, "from_environment", lambda: Settings())
    module.main()
    for dependency in dependencies:
        dependency.assert_not_called()


def test_cli_sem_api_configurada_para_antes_de_coletar(monkeypatch, dependencies):
    monkeypatch.setattr("sys.argv", ["svrs_collection", "--once"])
    monkeypatch.setattr(Settings, "from_environment", lambda: Settings(enabled=True))
    monkeypatch.delenv("FISCAL_SERVICE_BASE_URL", raising=False)
    with pytest.raises(ValueError, match="FISCAL_SERVICE_BASE_URL"):
        module.main()
    for dependency in dependencies:
        dependency.assert_not_called()


def test_ciclo_unico_retorna_codigo_de_erro_sem_repetir(monkeypatch, dependencies):
    monkeypatch.setattr("sys.argv", ["svrs_collection", "--once"])
    monkeypatch.setattr(Settings, "from_environment", lambda: Settings(enabled=True))
    monkeypatch.setenv("FISCAL_SERVICE_BASE_URL", "http://127.0.0.1:8081")
    _, history, dispatch = dependencies
    history.side_effect = TimeoutError()
    with pytest.raises(SystemExit) as exit:
        module.main()
    assert exit.value.code == 1
    history.assert_called_once()
    dispatch.assert_not_called()


@pytest.mark.parametrize("name,value", [("ENABLED", "yes"), ("BATCH_SIZE", "0"),
    ("BATCH_SIZE", "101"), ("INTERVAL_SECONDS", "0"), ("TIMEOUT_SECONDS", "nan")])
def test_rejeita_configuracao_invalida(monkeypatch, name, value):
    monkeypatch.setenv("SVRS_COLLECTION_" + name, value)
    with pytest.raises(ValueError):
        Settings.from_environment()
