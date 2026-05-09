# Проверка, что API доступно и логин работает.
# Использование:
#   .\scripts\check-api.ps1
#   .\scripts\check-api.ps1 -BaseUrl "http://127.0.0.1:8080"

param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"

$loginBody = @{
    email = "student@zona.local"
    password = "student123"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod `
        -Uri "$BaseUrl/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginBody `
        -TimeoutSec 20

    if ($res.token) {
        Write-Host "OK: API is up, login succeeded." -ForegroundColor Green
        Write-Host "User: $($res.user.displayName) [$($res.user.role)]"
    } else {
        Write-Host "WARN: API responded, but token missing." -ForegroundColor Yellow
    }
}
catch {
    Write-Host "ERROR: API check failed." -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}
