#Requires -Version 5.1
param(
    [Parameter(Mandatory=$true)][string]$PackageDir,
    [Parameter(Mandatory=$true)][string]$WorkDir,
    [string]$InstallDir = 'C:\Program Files\Phon'
)
$ErrorActionPreference = 'Stop'
$fixture = [IO.Path]::GetFullPath($WorkDir)
$package = [IO.Path]::GetFullPath($PackageDir)
if ($fixture -eq [IO.Path]::GetPathRoot($fixture) -or $fixture -eq $package) { throw 'Choose a separate test directory' }
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$testPackage = Join-Path $fixture 'package'
if (Test-Path -LiteralPath $testPackage) { throw 'Use a fresh WorkDir for each smoke test' }
New-Item -ItemType Directory -Path $testPackage -Force | Out-Null
foreach ($name in @('phon-zh-agent.jar','phon-zh-display.jar','resources','Install-PhonChinese.ps1')) {
    Copy-Item -LiteralPath (Join-Path $package $name) -Destination (Join-Path $testPackage $name) -Recurse
}
$programs = Join-Path $fixture 'Programs'
$desktop = Join-Path $fixture 'Desktop'
$localData = Join-Path $fixture 'LocalAppData'
New-Item -ItemType Directory -Path $programs,$desktop,$localData -Force | Out-Null
$scriptPath = Join-Path $testPackage 'Install-PhonChinese.ps1'
$source = Get-Content -LiteralPath $scriptPath -Raw -Encoding utf8
# Redirect only the two OS folder lookups, retaining the installer logic.
foreach ($pair in @(
    @("[Environment]::GetFolderPath('Programs')", $programs),
    @("[Environment]::GetFolderPath('Desktop')", $desktop)
)) {
    if (($source.Split(@($pair[0]),[StringSplitOptions]::None)).Count -ne 2) { throw 'Folder lookup changed: inspect the test harness before running' }
    $source = $source.Replace($pair[0], ("'" + $pair[1].Replace("'","''") + "'"))
}
# Exercise the closed-Phon branch without stopping any real user process.
$source = $source.Replace('$ErrorActionPreference = ''Stop''', '$ErrorActionPreference = ''Stop''' + "`r`nfunction Get-Process { param([string]`$Name) return @() }")
[IO.File]::WriteAllText($scriptPath,$source,[Text.UTF8Encoding]::new($true))
$previousLocalData = $env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA = $localData
    $shell = New-Object -ComObject WScript.Shell
    $legacyPath = Join-Path $programs 'Phon\Phon.lnk'
    New-Item -ItemType Directory -Path (Split-Path $legacyPath) -Force | Out-Null
    $legacy = $shell.CreateShortcut($legacyPath)
    $legacy.TargetPath = Join-Path $InstallDir 'Phon.exe'
    $legacy.Arguments = '-J-javaagent:"' + (Join-Path $localData 'Phon\localization-zh-CN\phon-zh-agent.jar') + '"'
    $legacy.Save()
    & $scriptPath -InstallDir $InstallDir
    if (Test-Path -LiteralPath $legacyPath) { throw 'Old module-owned shortcut was not migrated' }
    $links = @((Join-Path $desktop 'Phon（简体中文）.lnk'),(Join-Path $programs 'Phon\Phon（简体中文）.lnk'))
    foreach ($path in $links) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Shortcut was not created' }
        $link = $shell.CreateShortcut($path)
        if ($link.TargetPath -ne (Join-Path $InstallDir 'Phon.exe') -or $link.Arguments -notmatch '-J-javaagent:') { throw 'Incorrect shortcut target or parameters' }
    }
    foreach ($name in @('phon-zh-agent.jar','phon-zh-display.jar','resources\zh_CN.tsv')) {
        $original = Get-FileHash -LiteralPath (Join-Path $testPackage $name) -Algorithm SHA256
        $installed = Get-FileHash -LiteralPath (Join-Path $localData ('Phon\localization-zh-CN\' + $name)) -Algorithm SHA256
        if ($original.Hash -ne $installed.Hash) { throw 'Installed payload does not match the package' }
    }
    $legacy = $shell.CreateShortcut($legacyPath)
    $legacy.TargetPath = Join-Path $InstallDir 'Phon.exe'
    $legacy.Arguments = ''
    $legacy.Save()
    & $scriptPath -InstallDir $InstallDir -English
    if (-not (Test-Path -LiteralPath $legacyPath)) { throw 'Unrelated original shortcut was removed' }
    foreach ($path in $links) { if (Test-Path -LiteralPath $path) { throw 'Chinese shortcut remains after restoring English' } }
    if (-not (Test-Path -LiteralPath (Join-Path $localData 'Phon\localization-zh-CN\phon-zh-agent.jar'))) { throw 'Restore should retain the module files' }
    Write-Host 'PASS isolated installer: shortcuts, payload hashes, legacy migration, restore, retained module files, original shortcut preserved'
} finally { $env:LOCALAPPDATA = $previousLocalData }
