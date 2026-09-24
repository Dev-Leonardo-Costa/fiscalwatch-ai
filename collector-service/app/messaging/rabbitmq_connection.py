import pika


def create_rabbitmq_connection():
    credentials = pika.PlainCredentials(
        username="fiscalwatch",
        password="fiscalwatch"
    )

    parameters = pika.ConnectionParameters(
        host="localhost",
        port=5672,
        credentials=credentials
    )

    return pika.BlockingConnection(parameters)