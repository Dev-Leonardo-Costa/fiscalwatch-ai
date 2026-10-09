from unittest.mock import Mock

import pytest

from app.messaging import rabbitmq_connection


VARIABLES = (
    "RABBITMQ_HOST", "RABBITMQ_PORT", "RABBITMQ_USERNAME",
    "RABBITMQ_PASSWORD", "RABBITMQ_VIRTUAL_HOST"
)


@pytest.fixture(autouse=True)
def isolar_configuracao_e_impedir_conexao_real(monkeypatch):
    for variable in VARIABLES:
        monkeypatch.delenv(variable, raising=False)
    connection = Mock()
    monkeypatch.setattr(rabbitmq_connection.pika, "BlockingConnection", connection)
    return connection


def test_deve_preservar_configuracao_local_padrao(
    isolar_configuracao_e_impedir_conexao_real
):
    factory = isolar_configuracao_e_impedir_conexao_real
    assert rabbitmq_connection.create_rabbitmq_connection() is factory.return_value
    parameters = factory.call_args.args[0]
    assert parameters.host == "localhost"
    assert parameters.port == 5672
    assert parameters.virtual_host == "/"
    assert parameters.credentials.username == "fiscalwatch"
    assert parameters.credentials.password == "fiscalwatch"
    factory.assert_called_once()


def test_deve_configurar_broker_isolado_sem_registrar_credenciais(
    monkeypatch, caplog, isolar_configuracao_e_impedir_conexao_real
):
    values = ("127.0.0.1", "5673", "fiscalwatch_it", "test-only-password", "fiscalwatch_it")
    for variable, value in zip(VARIABLES, values):
        monkeypatch.setenv(variable, value)
    rabbitmq_connection.create_rabbitmq_connection()
    parameters = isolar_configuracao_e_impedir_conexao_real.call_args.args[0]
    assert parameters.host == "127.0.0.1"
    assert parameters.port == 5673
    assert parameters.virtual_host == "fiscalwatch_it"
    assert parameters.credentials.username == "fiscalwatch_it"
    assert parameters.credentials.password == "test-only-password"
    assert caplog.records == []


def test_deve_ler_variaveis_a_cada_chamada_e_preservar_espacos_da_senha(
    monkeypatch, isolar_configuracao_e_impedir_conexao_real
):
    rabbitmq_connection.create_rabbitmq_connection()
    monkeypatch.setenv("RABBITMQ_HOST", " 127.0.0.1 ")
    monkeypatch.setenv("RABBITMQ_PORT", " 5673 ")
    monkeypatch.setenv("RABBITMQ_PASSWORD", " test-password ")
    rabbitmq_connection.create_rabbitmq_connection()
    parameters = isolar_configuracao_e_impedir_conexao_real.call_args.args[0]
    assert parameters.host == "127.0.0.1"
    assert parameters.port == 5673
    assert parameters.credentials.password == " test-password "


@pytest.mark.parametrize("variable", VARIABLES)
@pytest.mark.parametrize("value", ["", " ", "\n\t"])
def test_deve_rejeitar_variavel_vazia_sem_conectar(
    variable, value, monkeypatch, isolar_configuracao_e_impedir_conexao_real
):
    monkeypatch.setenv(variable, value)
    with pytest.raises(ValueError, match=variable):
        rabbitmq_connection.create_rabbitmq_connection()
    isolar_configuracao_e_impedir_conexao_real.assert_not_called()


@pytest.mark.parametrize("port", ["0", "65536", "-1", "+5672", "5.5", "abc", "５６７２", "9" * 100])
def test_deve_rejeitar_porta_invalida_sem_expor_valor(
    port, monkeypatch, caplog, isolar_configuracao_e_impedir_conexao_real
):
    monkeypatch.setenv("RABBITMQ_PORT", port)
    monkeypatch.setenv("RABBITMQ_PASSWORD", "test-secret-not-for-logs")
    with pytest.raises(ValueError) as error:
        rabbitmq_connection.create_rabbitmq_connection()
    assert str(error.value) == "RABBITMQ_PORT deve ser um inteiro entre 1 e 65535"
    assert "test-secret-not-for-logs" not in str(error.value)
    assert caplog.records == []
    isolar_configuracao_e_impedir_conexao_real.assert_not_called()


@pytest.mark.parametrize("port", ["1", "65535"])
def test_deve_aceitar_limites_validos_da_porta(
    port, monkeypatch, isolar_configuracao_e_impedir_conexao_real
):
    monkeypatch.setenv("RABBITMQ_PORT", port)
    rabbitmq_connection.create_rabbitmq_connection()
    assert isolar_configuracao_e_impedir_conexao_real.call_args.args[0].port == int(port)
