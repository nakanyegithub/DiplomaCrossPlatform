# Быстрый запуск сервера Zona на встроенной H2 (без PostgreSQL).
# Использование:
#   .\scripts\run-server.ps1
#   .\scripts\run-server.ps1 -Port 8081 -DataDir ".\data-dev"

param(
    [int]$Port = 8080,
    [string]$DataDir = ".\data"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$env:PORT = "$Port"
$env:ZONA_H2_DIR = $DataDir

Write-Host "Starting Zona server..." -ForegroundColor Cyan
Write-Host "PORT=$($env:PORT), ZONA_H2_DIR=$($env:ZONA_H2_DIR)" -ForegroundColor DarkCyan
Write-Host "API: http://127.0.0.1:$Port" -ForegroundColor Green
Write-Host "For Android emulator client use: http://10.0.2.2:$Port" -ForegroundColor Green
Write-Host ""

& ".\gradlew.bat" :server:run
