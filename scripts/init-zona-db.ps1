# Создаёт пользователя zona / базу zona под те же учётные данные, что ждёт :server:run.
# Пример (пароль суперпользователя postgres):
#   $env:PGPASSWORD = "ваш_пароль_postgres"
#   .\scripts\init-zona-db.ps1

param(
    [string]$PostgresPassword,
    [string]$SuperUser = "postgres",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
)

$ErrorActionPreference = "Stop"
$sql = Join-Path $PSScriptRoot "init-zona-db.sql"

if (-not (Test-Path $PsqlPath)) {
    $alt = Get-Command psql -ErrorAction SilentlyContinue
    if ($alt) { $PsqlPath = $alt.Source }
    else { throw "Не найден psql. Укажите -PsqlPath или добавьте PostgreSQL\bin в PATH." }
}

if ($PostgresPassword) {
    $env:PGPASSWORD = $PostgresPassword
}

if (-not $env:PGPASSWORD) {
    Write-Host "Подсказка: если psql спросит пароль — задайте `$env:PGPASSWORD или параметр -PostgresPassword"
}

& $PsqlPath -U $SuperUser -d postgres -v ON_ERROR_STOP=1 -f $sql
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Готово: база zona, пользователь zona / zona"
