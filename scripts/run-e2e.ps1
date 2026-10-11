$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $env:LOCALAPPDATA 'NewsManagementApi/local-config.xml'
$variableNames = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'NEWS_E2E_ENABLED', 'DEBUG', 'TRACE')
$original = @{}
foreach ($name in $variableNames) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$exitCode = 1
try {
    try {
        $config = Import-Clixml -LiteralPath $configPath
        $password = [System.Net.NetworkCredential]::new('', $config.DB_PASSWORD).Password
        $secret = [System.Net.NetworkCredential]::new('', $config.JWT_SECRET).Password
        if ([string]::IsNullOrWhiteSpace($password) -or
            [string]::IsNullOrWhiteSpace($config.DB_USERNAME) -or
            [string]::IsNullOrWhiteSpace($secret) -or [Text.Encoding]::UTF8.GetByteCount($secret) -lt 32) {
            throw 'Invalid configuration'
        }
        $dbUrl = [regex]::Replace([string]$config.DB_URL,
            '(?<=/)(news_management)(?=\?|$)', 'news_management_e2e')
        if ($dbUrl -notmatch '^jdbc:mysql://[^/]+/news_management_e2e(?:\?.*)?$') {
            throw 'Unsafe database URL'
        }
    } catch {
        throw 'Cannot decrypt valid DPAPI configuration or verify news_management_e2e URL. No tests started.'
    }
    $env:DB_URL = $dbUrl
    $env:DB_USERNAME = [string]$config.DB_USERNAME
    $env:DB_PASSWORD = $password
    $env:JWT_SECRET = $secret
    $env:NEWS_E2E_ENABLED = 'true'
    # Prevent inherited Spring debug/trace from logging test authentication responses.
    $env:DEBUG = 'false'
    $env:TRACE = 'false'
    Write-Host 'E2E target verified: news_management_e2e (secrets not displayed).'
    Push-Location $workspace
    try {
        # Java preflight checks SELECT DATABASE() before starting Hibernate/HTTP.
        & mvn clean verify -Pe2e
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} catch {
    Write-Host 'E2E stopped: configuration/decryption/launcher failure. No secret details displayed.'
} finally {
    foreach ($name in $variableNames) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
    $password = $null
    $secret = $null
    $config = $null
}
exit $exitCode
