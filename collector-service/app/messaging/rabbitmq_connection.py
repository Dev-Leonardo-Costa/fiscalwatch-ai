import os

import pika


def _environment_value(name: str, default: str) -> str:
    value = os.getenv(name, default)
    if not value.strip():
        raise ValueError(f"{name} não pode estar vazio")
    return value


def create_rabbitmq_connection():
    host = _environment_value("RABBITMQ_HOST", "localhost").strip()
    raw_port = _environment_value("RABBITMQ_PORT", "5672").strip()
    if not raw_port.isascii() or not raw_port.isdecimal() or len(raw_port) > 5:
        raise ValueError("RABBITMQ_PORT deve ser um inteiro entre 1 e 65535")
    port = int(raw_port)
    if not 1 <= port <= 65535:
        raise ValueError("RABBITMQ_PORT deve ser um inteiro entre 1 e 65535")

    username = _environment_value("RABBITMQ_USERNAME", "fiscalwatch")
    password = _environment_value("RABBITMQ_PASSWORD", "fiscalwatch")
    virtual_host = _environment_value("RABBITMQ_VIRTUAL_HOST", "/")

    credentials = pika.PlainCredentials(
        username=username,
        password=password
    )

    parameters = pika.ConnectionParameters(
        host=host,
        port=port,
        virtual_host=virtual_host,
        credentials=credentials
    )

    return pika.BlockingConnection(parameters)
