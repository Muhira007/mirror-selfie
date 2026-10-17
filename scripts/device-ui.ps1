param(
    [ValidateSet('ui','tap','shot','pair')][string]$Action = 'ui',
    [string]$Serial = 'CI5XNJAYFA7HBIHI',
    [string]$MonitorSerial = '34258863370002T',
    [string]$Match,
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
function Read-TaskUi([string]$Device) {
    for ($taskAttempt = 0; $taskAttempt -lt 3; $taskAttempt++) {
        & $taskAdb -s $Device shell rm -f /data/local/tmp/mirror-p1-ui.xml | Out-Null
        $taskDumpOutput = & $taskAdb -s $Device shell uiautomator dump /data/local/tmp/mirror-p1-ui.xml 2>&1
        if ($LASTEXITCODE -ne 0 -or ($taskDumpOutput -join '') -notmatch 'dumped to') { continue }
        $taskRaw = & $taskAdb -s $Device shell cat /data/local/tmp/mirror-p1-ui.xml
        if ($LASTEXITCODE -eq 0) { return [xml]($taskRaw -join "`n") }
    }
    throw 'No fresh UI dump available; previous UI data will not be reused'
}
switch ($Action) {
    'ui' {
        $taskUi = Read-TaskUi $Serial
        $taskUi.SelectNodes('//node') | Where-Object { ($_.text -or $_.'content-desc') -and $_.'content-desc' -ne 'pairing-code' -and $_.text -notlike 'mirror1:*' } |
            Select-Object text,content-desc,resource-id,bounds,clickable | ConvertTo-Json -Depth 3
    }
    'tap' {
        if (-not $Match) { throw 'Match is required' }
        $taskUi = Read-TaskUi $Serial
        $taskNode = $taskUi.SelectNodes('//node') | Where-Object { $_.text -eq $Match -or $_.'content-desc' -eq $Match -or $_.'resource-id' -eq $Match } | Select-Object -First 1
        if (-not $taskNode) { throw "Control not found: $Match" }
        $taskBounds = [regex]::Match($taskNode.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $taskBounds.Success) { throw 'No control bounds' }
        $taskX = [int]( ([int]$taskBounds.Groups[1].Value + [int]$taskBounds.Groups[3].Value) / 2 )
        $taskY = [int]( ([int]$taskBounds.Groups[2].Value + [int]$taskBounds.Groups[4].Value) / 2 )
        & $taskAdb -s $Serial shell input -d 0 tap $taskX $taskY
    }
    'shot' {
        if (-not $OutputPath) { throw 'OutputPath is required' }
        & $taskAdb -s $Serial shell screencap -p /data/local/tmp/mirror-p1.png
        & $taskAdb -s $Serial pull /data/local/tmp/mirror-p1.png $OutputPath
    }
    'pair' {
        # Transfer the full pinned connection code in memory over the authorized USB test channel.
        # No logs or files contain the token. Runtime media still travels directly over Wi-Fi.
        $taskCoreJar = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.google.zxing\core\3.5.3') -Recurse -Filter 'core-3.5.3.jar' | Select-Object -First 1
        if (-not $taskCoreJar) { throw 'Build dependencies first to obtain ZXing core' }
        $taskToolsDir = Join-Path (Split-Path $PSScriptRoot) 'artifacts\tools'
        New-Item -ItemType Directory -Path $taskToolsDir -Force | Out-Null
        & javac -cp $taskCoreJar.FullName -d $taskToolsDir (Join-Path $PSScriptRoot 'PairPhones.java')
        if ($LASTEXITCODE -ne 0) { throw 'Could not compile the USB pairing helper' }
        & java --class-path ($taskCoreJar.FullName + [IO.Path]::PathSeparator + $taskToolsDir) PairPhones $taskAdb $Serial $MonitorSerial
        if ($LASTEXITCODE -ne 0) { throw 'Could not decode the visible QR and pair the monitor' }
    }
}
