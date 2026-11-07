$ErrorActionPreference = 'Stop'

function Start-TestServices {
    param([string]$RepositoryRoot)
    if ($env:MEDICAL_TEST_DB_URL -and $env:MEDICAL_TEST_REDIS_PORT) { return $null }
    if (-not $IsWindows) { throw 'Set MEDICAL_TEST_DB_URL, MEDICAL_TEST_DB_USER, MEDICAL_TEST_DB_PASSWORD and MEDICAL_TEST_REDIS_PORT for isolated test services.' }
    $mysqlBin = if ($env:MEDICAL_MYSQL_BIN) { $env:MEDICAL_MYSQL_BIN } else { 'D:\MySQL\mysql-26.7.0-winx64\bin' }
    $redisBin = if ($env:MEDICAL_REDIS_BIN) { $env:MEDICAL_REDIS_BIN } else { 'D:\Redis' }
    if (-not (Test-Path "$mysqlBin\mysqld.exe") -or -not (Test-Path "$redisBin\redis-server.exe")) { throw 'Set MEDICAL_MYSQL_BIN and MEDICAL_REDIS_BIN to installed server executables.' }
    $runParent = if (Test-Path 'D:\') { 'D:\' } else { [IO.Path]::GetTempPath() }
    $runRoot = Join-Path $runParent ('medical-mvp-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $mysqlData = Join-Path $runRoot 'mysql'
    & "$mysqlBin\mysqld.exe" --no-defaults --initialize-insecure "--basedir=$([IO.Path]::GetDirectoryName($mysqlBin))" "--datadir=$mysqlData" '--innodb-redo-log-capacity=64M' "--log-error=$runRoot/mysql-init.log"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed; inspect $runRoot/mysql-init.log" }
    $mysqlPort = Get-FreePort
    $redisPort = Get-FreePort
    $mysql = Start-Process -FilePath "$mysqlBin\mysqld.exe" -WorkingDirectory $runRoot -ArgumentList @('--no-defaults', "--basedir=`"$([IO.Path]::GetDirectoryName($mysqlBin))`"", "--datadir=`"$mysqlData`"", '--innodb-redo-log-capacity=64M', "--port=$mysqlPort", '--bind-address=127.0.0.1', '--mysqlx=0', "--log-error=`"$runRoot/mysql.log`"") -PassThru -WindowStyle Hidden -RedirectStandardError "$runRoot/mysql-stderr.log"
    $redis = Start-Process -FilePath "$redisBin\redis-server.exe" -WorkingDirectory $runRoot -ArgumentList @('--port', $redisPort, '--bind', '127.0.0.1', '--save', '""', '--appendonly', 'no', '--dir', "`"$runRoot`"") -PassThru -WindowStyle Hidden -RedirectStandardOutput "$runRoot/redis.log" -RedirectStandardError "$runRoot/redis-error.log"
    $services = @{ MySql = $mysql; Redis = $redis; Root = $runRoot; MysqlData = $mysqlData }
    try {
        Wait-TestPort $mysqlPort $mysql
        Wait-TestPort $redisPort $redis
        & "$mysqlBin\mysql.exe" --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --execute="CREATE DATABASE medical_identity_test CHARACTER SET utf8mb4;"
        if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated test schema.' }
        $env:MEDICAL_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/medical_identity_test?connectionTimeZone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
        $env:MEDICAL_TEST_DB_USER = 'root'
        $env:MEDICAL_TEST_DB_PASSWORD = ''
        $env:MEDICAL_TEST_REDIS_PORT = "$redisPort"
        return $services
    } catch { Stop-TestServices $services; throw }
}

function Get-FreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Wait-TestPort([int]$Port, $Process) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($Process.HasExited) { throw "Test service exited before opening port $Port" }
        $client = [Net.Sockets.TcpClient]::new()
        try { $client.Connect('127.0.0.1', $Port); return } catch { Start-Sleep -Milliseconds 500 } finally { $client.Dispose() }
    }
    throw "Test service did not open port $Port"
}

function Stop-TestServices($Services) {
    if (-not $Services) { return }
    # Start-Process can leave a mysqld child after its PowerShell handle exits. Restrict
    # cleanup to this run's generated data directory so installed MySQL instances survive.
    if ($IsWindows -and $Services.Root) {
        $paths = @($Services.Root, $Services.MysqlData) | Where-Object { $_ } | ForEach-Object { [regex]::Escape([IO.Path]::GetFullPath($_)) }
        $pattern = ($paths -join '|')
        foreach ($candidate in @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe' OR Name='redis-server.exe'" -ErrorAction SilentlyContinue)) {
            if ($candidate.CommandLine -and $candidate.CommandLine -match $pattern) {
                Stop-Process -Id $candidate.ProcessId -Force -ErrorAction SilentlyContinue
            }
        }
    }
    foreach ($process in @($Services.MySql, $Services.Redis)) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
    if ($Services.Root) { Remove-Item -LiteralPath $Services.Root -Recurse -Force -ErrorAction SilentlyContinue }
    Remove-Item Env:MEDICAL_TEST_DB_URL,Env:MEDICAL_TEST_DB_USER,Env:MEDICAL_TEST_DB_PASSWORD,Env:MEDICAL_TEST_REDIS_PORT -ErrorAction SilentlyContinue
}
