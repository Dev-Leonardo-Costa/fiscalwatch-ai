# Conexão RabbitMQ do collector-service

`create_rabbitmq_connection()` lê o ambiente a cada chamada, seguindo o uso
existente de `os.getenv` no collector-service. Não carrega arquivos `.env`.

| Variável | Padrão local |
| --- | --- |
| RABBITMQ_HOST | localhost |
| RABBITMQ_PORT | 5672 |
| RABBITMQ_USERNAME | usuário local existente |
| RABBITMQ_PASSWORD | senha local existente |
| RABBITMQ_VIRTUAL_HOST | / |

Sem variáveis definidas, o comportamento local anterior permanece. Valores
vazios ou só whitespace são rejeitados; porta aceita dígitos ASCII e intervalo
1–65535. Host e porta removem whitespace externo. Usuário, senha e vhost
preservam seus caracteres. Erros de validação não incluem valores ou senhas.
Não há logging de parâmetros neste componente; erros de conexão do Pika são
propagados sem adicionar mensagens com credenciais.

## PowerShell: ambiente isolado

Defina no mesmo terminal usado para iniciar o collector-service:

```powershell
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '5673'
$env:RABBITMQ_USERNAME = 'fiscalwatch_it'
$env:RABBITMQ_VIRTUAL_HOST = 'fiscalwatch_it'
$testPassword = Read-Host 'Senha do RabbitMQ isolado' -AsSecureString
$env:RABBITMQ_PASSWORD = [System.Net.NetworkCredential]::new('', $testPassword).Password
Remove-Variable testPassword
```

Use a senha exclusiva de desenvolvimento do Compose isolado. Não use segredos
reais nem armazene a senha em arquivos versionados. Definir essas variáveis
não conecta, publica mensagens ou altera o broker. Elas só afetam processos
filhos deste terminal; processos Python existentes precisam ser iniciados
com o ambiente correspondente.

Para restaurar os padrões, remova as cinco variáveis do terminal:

```powershell
'RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USERNAME',
'RABBITMQ_PASSWORD', 'RABBITMQ_VIRTUAL_HOST' | ForEach-Object {
    Remove-Item -LiteralPath "Env:$_" -ErrorAction SilentlyContinue
}
```

Exchange, routing key, filas, retry e DLQ não são alterados por esta configuração.
Os testes verificam os parâmetros com BlockingConnection simulado; não comprovam
autenticação ou conectividade real ao broker.
