import httpx
import pytest

from app.model.schema_comparison import SchemaChange
from app.model.svrs_schema_comparison_orchestration import (
    SvrsSchemaComparisonOrchestrationResult
)
from app.model.svrs_schema_identity import SvrsSchemaPackageIdentity
from app.service.fiscal_service_publication_history_client import (
    FISCAL_SERVICE_BASE_URL_ENV
)
from app.service.fiscal_service_schema_impact_client import (
    send_schema_comparison_impact_analysis
)


def test_deve_enviar_payload_da_comparacao_de_schema(monkeypatch):
    chamadas = []

    def fake_post(url, json, timeout):
        chamadas.append({
            "url": url,
            "json": json,
            "timeout": timeout
        })

        return RespostaHttp({"id": 1})

    monkeypatch.setenv(
        FISCAL_SERVICE_BASE_URL_ENV,
        "http://fiscal-service:8080"
    )
    monkeypatch.setattr(
        "app.service.fiscal_service_schema_impact_client.httpx.post",
        fake_post
    )

    result = send_schema_comparison_impact_analysis(criar_comparacao())

    assert result == {"id": 1}
    assert chamadas == [
        {
            "url": (
                "http://fiscal-service:8080"
                "/api/impact-analyses/schema-comparisons"
            ),
            "json": {
                "currentExternalId": "current-id",
                "previousExternalId": "previous-id",
                "currentVersion": "2025.002 v1.30",
                "previousVersion": "2025.002 v1.20",
                "changes": [
                    {
                        "artifact": "DFeTiposBasicos_v1.00.xsd",
                        "changeType": "TYPE_CHANGED",
                        "schemaPath": "complexType:TCIBS/element:vBC",
                        "symbolName": "vBC",
                        "before": "TDec1302",
                        "after": "TDec1302RTC"
                    },
                    {
                        "artifact": "leiauteNFe_v4.00.xsd",
                        "changeType": "ELEMENT_ADDED",
                        "schemaPath": (
                            "complexType:TNFe/element:infNFe"
                            "/element:ide/element:dPrevEntrega"
                        ),
                        "symbolName": "dPrevEntrega",
                        "before": None,
                        "after": {
                            "name": "dPrevEntrega",
                            "type": "TData"
                        }
                    }
                ]
            },
            "timeout": 10.0
        }
    ]


def test_deve_usar_url_base_informada_explicitamente(monkeypatch):
    chamadas = []

    def fake_post(url, json, timeout):
        chamadas.append(url)

        return RespostaHttp({"id": 1})

    monkeypatch.setattr(
        "app.service.fiscal_service_schema_impact_client.httpx.post",
        fake_post
    )

    send_schema_comparison_impact_analysis(
        criar_comparacao(),
        base_url="http://localhost:8080/"
    )

    assert chamadas == [
        "http://localhost:8080/api/impact-analyses/schema-comparisons"
    ]


def test_deve_propagar_status_http_de_erro(monkeypatch):
    error = httpx.HTTPStatusError(
        "500 Server Error",
        request=httpx.Request("POST", "http://localhost"),
        response=httpx.Response(500)
    )

    monkeypatch.setattr(
        "app.service.fiscal_service_schema_impact_client.httpx.post",
        lambda url, json, timeout: RespostaHttp({"erro": True}, error)
    )

    with pytest.raises(httpx.HTTPStatusError) as exc_info:
        send_schema_comparison_impact_analysis(
            criar_comparacao(),
            base_url="http://localhost:8080"
        )

    assert exc_info.value is error


def test_deve_nao_enviar_resultado_que_nao_esteja_compared(monkeypatch):
    chamadas = []

    monkeypatch.setattr(
        "app.service.fiscal_service_schema_impact_client.httpx.post",
        lambda url, json, timeout: chamadas.append(url)
    )

    result = send_schema_comparison_impact_analysis(
        criar_comparacao(status="SKIPPED"),
        base_url="http://localhost:8080"
    )

    assert result is None
    assert chamadas == []


class RespostaHttp:

    def __init__(self, payload, error=None):
        self.payload = payload
        self.error = error

    def raise_for_status(self):
        if self.error:
            raise self.error

    def json(self):
        return self.payload


def criar_comparacao(status="COMPARED"):
    return SvrsSchemaComparisonOrchestrationResult(
        status=status,
        current_identity=SvrsSchemaPackageIdentity(
            nt="2025.002",
            raw_version="v1.30",
            status="RESOLVED"
        ),
        previous_identity=SvrsSchemaPackageIdentity(
            nt="2025.002",
            raw_version="v1.20",
            status="RESOLVED"
        ),
        current_external_id="current-id",
        previous_external_id="previous-id",
        changes=[
            SchemaChange(
                artifact="DFeTiposBasicos_v1.00.xsd",
                change_type="TYPE_CHANGED",
                schema_path="complexType:TCIBS/element:vBC",
                symbol_name="vBC",
                before="TDec1302",
                after="TDec1302RTC"
            ),
            SchemaChange(
                artifact="leiauteNFe_v4.00.xsd",
                change_type="ELEMENT_ADDED",
                schema_path=(
                    "complexType:TNFe/element:infNFe"
                    "/element:ide/element:dPrevEntrega"
                ),
                symbol_name="dPrevEntrega",
                before=None,
                after={
                    "name": "dPrevEntrega",
                    "type": "TData"
                }
            )
        ],
        total_changes=2
    )
