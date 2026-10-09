"""Executor separado do Uvicorn; nenhuma tarefa começa durante importação."""
import argparse
import logging
import math
import os
import threading
import time
from dataclasses import dataclass
import pika

from app.collectors.svrs_collector import get_publications
from app.service.publication_dispatch_service import dispatch_publication
from app.messaging.publication_event_publisher import PublicationConnectionError
from app.service.svrs_collection_state_client import fetch_collection_state
from app.service.fiscal_service_publication_history_client import _resolve_base_url

logger = logging.getLogger(__name__)
_cycle_lock = threading.Lock()


@dataclass(frozen=True)
class Settings:
    enabled: bool = False
    interval_seconds: float = 300
    batch_size: int = 5
    timeout_seconds: float = 30

    def __post_init__(self):
        for value in (self.interval_seconds, self.timeout_seconds):
            if not math.isfinite(value) or value <= 0:
                raise ValueError("Intervalo e timeout devem ser positivos e finitos")
        if not 1 <= self.batch_size <= 100:
            raise ValueError("Lote deve estar entre 1 e 100")

    @classmethod
    def from_environment(cls):
        enabled = os.getenv("SVRS_COLLECTION_ENABLED", "false").lower().strip()
        if enabled not in ("true", "false"):
            raise ValueError("SVRS_COLLECTION_ENABLED deve ser true ou false")
        return cls(enabled == "true",
                   float(os.getenv("SVRS_COLLECTION_INTERVAL_SECONDS", "300")),
                   int(os.getenv("SVRS_COLLECTION_BATCH_SIZE", "5")),
                   float(os.getenv("SVRS_COLLECTION_TIMEOUT_SECONDS", "30")))


class SvrsCollectionJob:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._cursor = None
        # Não republicar quando a confirmação/persistência permanece ambígua.
        self._awaiting_persistence = {}

    def run_once(self):
        result = {"published": 0, "failed": 0, "checked": 0}
        if not self.settings.enabled or not _cycle_lock.acquire(blocking=False):
            return result
        try:
            publications = {p.external_id: p for p in get_publications(
                timeout=self.settings.timeout_seconds) if p.source == "SVRS"}
            ordered = sorted(publications.values(), key=lambda p: p.external_id)
            if self._cursor:
                ordered = ([p for p in ordered if p.external_id > self._cursor]
                           + [p for p in ordered if p.external_id <= self._cursor])
            attempted = 0
            # Também limita consultas quando todas as publicações já estão prontas.
            for publication in ordered[:self.settings.batch_size * 10]:
                try:
                    state = fetch_collection_state(publication.external_id,
                                                   timeout=self.settings.timeout_seconds)
                except Exception as error:
                    logger.warning("Ciclo interrompido: consulta Java falhou; tipo=%s",
                                   type(error).__name__)
                    result["failed"] += 1
                    break
                result["checked"] += 1
                self._cursor = publication.external_id
                expected = self._awaiting_persistence.get(publication.external_id)
                if state.valid_document or (state.exists and expected in ("FAILED", "PENDING")
                                            and state.extraction_status == expected):
                    self._awaiting_persistence.pop(publication.external_id, None)
                if publication.external_id in self._awaiting_persistence:
                    logger.warning("Persistência não confirmada; sem reenvio; external_id=%s",
                                   publication.external_id)
                    continue
                if state.valid_document:
                    continue
                if state.exists and state.extraction_status not in (None, "FAILED", "PENDING"):
                    # EMPTY e EXTRACTED inválido não são recuperáveis por processEvent.
                    continue
                if not publication.download_url:
                    continue
                attempted += 1
                # Marca antes do envio: timeout pode ocorrer após aceitação do broker.
                self._awaiting_persistence[publication.external_id] = "UNKNOWN"
                try:
                    dispatched = dispatch_publication(
                        publication, timeout=self.settings.timeout_seconds,
                        analyze_schema=False, recover_only=state.exists)
                    if dispatched["published"]:
                        self._awaiting_persistence[publication.external_id] = dispatched["document"]["extraction_status"]
                        result["published"] += 1
                        logger.info("Envio confirmado pelo broker; external_id=%s",
                                    publication.external_id)
                    else:
                        self._awaiting_persistence.pop(publication.external_id, None)
                        logger.info("Recuperação ainda sem conteúdo válido; external_id=%s",
                                    publication.external_id)
                except Exception as error:
                    if isinstance(error, (pika.exceptions.NackError,
                                          pika.exceptions.UnroutableError,
                                          PublicationConnectionError)):
                        # Rejeição conhecida / conexão não aberta: tentar em ciclo posterior.
                        self._awaiting_persistence.pop(publication.external_id, None)
                    result["failed"] += 1
                    logger.warning("Despacho falhou; external_id=%s tipo=%s",
                                   publication.external_id, type(error).__name__)
                if attempted >= self.settings.batch_size:
                    break
        except Exception as error:
            result["failed"] += 1
            logger.warning("Coleta falhou; tipo=%s", type(error).__name__)
        finally:
            _cycle_lock.release()
        logger.info("Ciclo SVRS encerrado; consultadas=%s enviadas=%s falhas=%s",
                    result["checked"], result["published"], result["failed"])
        return result


def main():
    parser = argparse.ArgumentParser(description="Coleta seletiva SVRS")
    parser.add_argument("--once", action="store_true", help="Um ciclo; requer enabled=true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    settings = Settings.from_environment()
    if not settings.enabled:
        logger.info("Coleta periódica SVRS desativada")
        return
    _resolve_base_url(None)  # Rejeita configuração ausente antes de consultar o portal.
    job = SvrsCollectionJob(settings)
    while True:
        result = job.run_once()
        if args.once:
            if result["failed"]:
                raise SystemExit(1)
            return
        time.sleep(settings.interval_seconds)


if __name__ == "__main__":
    main()
