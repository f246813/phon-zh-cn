#Requires -Version 5.1
param(
    [string]$InstallDir = 'C:\Program Files\Phon',
    [switch]$English
)
$ErrorActionPreference = 'Stop'
$exePath = Join-Path $InstallDir 'Phon.exe'
$langDir = Join-Path $env:LOCALAPPDATA 'Phon\localization-zh-CN'
$menuDir = Join-Path ([Environment]::GetFolderPath('Programs')) 'Phon'
$legacyMenuLink = Join-Path $menuDir 'Phon.lnk'
$agentPath = Join-Path $langDir 'phon-zh-agent.jar'
$links = @(
    (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Phon（简体中文）.lnk'),
    (Join-Path $menuDir 'Phon（简体中文）.lnk')
)
$shortcutShell = New-Object -ComObject WScript.Shell

if ($English) {
    foreach ($path in ($links + @($legacyMenuLink))) {
        if (Test-Path -LiteralPath $path) {
            $link = $shortcutShell.CreateShortcut($path)
            if ($link.Arguments.IndexOf($agentPath, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                Remove-Item -LiteralPath $path
            }
        }
    }
    Write-Host '已移除本包创建的中文快捷方式，请从原版 Phon 入口打开'
    exit
}

if (-not (Test-Path -LiteralPath $exePath -PathType Leaf)) { throw "未找到 Phon：$InstallDir" }
$metadataJar = Join-Path $InstallDir 'lib\phon-metapackage-4.1.1.jar'
if (-not (Test-Path -LiteralPath $metadataJar -PathType Leaf)) { throw '此包仅适配 Phon 4.1.1，请核对安装目录和版本' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$metadata = [IO.Compression.ZipFile]::OpenRead($metadataJar)
try {
    $entry = $metadata.GetEntry('phon.build.properties')
    if ($null -eq $entry) { throw '无法读取 Phon 版本信息' }
    $reader = New-Object IO.StreamReader($entry.Open())
    try { $versionText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($versionText -notmatch '(?m)^phon\.app\.version=4\.1\.1(?:\+[^\r\n]*)?\s*$') { throw '目标程序不是受支持的 Phon 4.1.1' }
} finally { $metadata.Dispose() }

if (Get-Process Phon -ErrorAction SilentlyContinue) { throw '请先完全退出 Phon，再安装或更新汉化包' }
foreach ($relative in @('phon-zh-agent.jar','phon-zh-display.jar','resources\zh_CN.tsv')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $relative) -PathType Leaf)) { throw "汉化包缺少文件：$relative，请先完整解压" }
}
foreach ($path in $links) {
    if (Test-Path -LiteralPath $path) {
        $existing = $shortcutShell.CreateShortcut($path)
        if ($existing.Arguments.IndexOf($agentPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) { throw "已存在其他同名快捷方式，请先重命名：$path" }
    }
}
New-Item -ItemType Directory -Path (Join-Path $langDir 'resources'),$menuDir -Force | Out-Null
if (Test-Path -LiteralPath $legacyMenuLink) {
    $legacyLink = $shortcutShell.CreateShortcut($legacyMenuLink)
    if ($legacyLink.Arguments.IndexOf($agentPath, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        Remove-Item -LiteralPath $legacyMenuLink
    }
}
foreach ($relative in @('phon-zh-agent.jar','phon-zh-display.jar','resources\zh_CN.tsv')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $relative) -Destination (Join-Path $langDir $relative) -Force
}
$launchArguments = '-J-javaagent:"' + (Join-Path $langDir 'phon-zh-agent.jar') + '" -J-Duser.language=zh -J-Duser.country=CN'
foreach ($path in $links) {
    $link = $shortcutShell.CreateShortcut($path)
    $link.TargetPath = $exePath
    $link.Arguments = $launchArguments
    $link.WorkingDirectory = $InstallDir
    $link.IconLocation = $exePath + ',0'
    $link.Description = 'Phon 4.1.1 简体中文显示补丁'
    $link.Save()
}
Write-Host '安装完成，请从“Phon（简体中文）”打开'
