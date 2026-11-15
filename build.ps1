# CandyTools build script (no Gradle).
# Pipeline: aapt2 compile -> aapt2 link -> javac -> jar -> d8 -> zip + zipalign -> apksigner
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File build.ps1
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Sdk "D:\Android\Sdk" -Jdk "C:\Java\jdk17"
#
# Signing (optional, from environment variables):
#   $env:CANDY_KS_PASS  = "<keystore password>"
#   $env:CANDY_KEY_PASS = "<key password>"
#   $env:CANDY_KS       = "<path to .jks>"   (default: keystore\candy.jks)
#   $env:CANDY_KS_ALIAS = "<alias>"          (default: candy)
# If the passwords are not set, an unsigned APK is produced.

param(
    [string]$Sdk = "C:\droid\sdk",
    [string]$Jdk = "C:\droid\jdk\jdk-17.0.20.1+1",
    [string]$OutName = "CandyTools.apk",
    [string]$KsPass = $env:CANDY_KS_PASS,
    [string]$KeyPass = $env:CANDY_KEY_PASS,
    [string]$KsPath = $env:CANDY_KS,
    [string]$KsAlias = $(if ($env:CANDY_KS_ALIAS) { $env:CANDY_KS_ALIAS } else { "candy" }),
    [int]$VersionCode = 0,
    [string]$VersionName = ""
)

$ErrorActionPreference = "Continue"

$Proj       = Split-Path -Parent $MyInvocation.MyCommand.Path
$Bt         = Join-Path $Sdk "build-tools\android-14"
$AndroidJar = Join-Path $Sdk "platform\android-34\android.jar"
$Build      = Join-Path $Proj "build"

if (-not (Test-Path $Bt)) { throw "build-tools not found: $Bt (use -Sdk)" }
if (-not (Test-Path $AndroidJar)) { throw "android.jar not found: $AndroidJar" }
if (-not (Test-Path $Jdk)) { throw "JDK not found: $Jdk (use -Jdk)" }

$env:JAVA_HOME = $Jdk
$env:PATH = (Join-Path $Jdk "bin") + ";" + $Bt + ";" + $env:PATH
if (-not $env:TMP -or -not (Test-Path $env:TMP)) { $env:TMP = $env:TEMP }

# 资源目录：aapt2 -A 要求目录存在，空目录 Git 无法保存，这里自动补建
New-Item -ItemType Directory -Force -Path "$Proj\assets" | Out-Null

if (Test-Path $Build) { Remove-Item -Recurse -Force $Build }
New-Item -ItemType Directory -Force -Path $Build, "$Build\gen", "$Build\classes", "$Build\compiled", "$Build\dex" | Out-Null

Write-Host "[1/6] aapt2 compile"
& "$Bt\aapt2.exe" compile --dir "$Proj\res" -o "$Build\compiled"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/6] aapt2 link"
$linkArgs = @(
    "link", "-o", "$Build\base.apk",
    "-I", $AndroidJar,
    "--manifest", "$Proj\AndroidManifest.xml",
    "--java", "$Build\gen",
    "-A", "$Proj\assets",
    "--min-sdk-version", "21",
    "--target-sdk-version", "33"
)
if ($VersionCode -gt 0) { $linkArgs += @("--version-code", "$VersionCode") }
if ($VersionName)       { $linkArgs += @("--version-name", $VersionName) }
$flat = Get-ChildItem "$Build\compiled" -Filter *.flat | ForEach-Object { $_.FullName }
& "$Bt\aapt2.exe" @linkArgs $flat
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] javac"
$srcs = Get-ChildItem -Recurse -Path "$Proj\src", "$Build\gen" -Filter *.java | ForEach-Object { $_.FullName }
& "$Jdk\bin\javac.exe" --release 8 -encoding UTF-8 -nowarn -classpath $AndroidJar -d "$Build\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/6] jar + d8"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = "$Build\classes.jar"
if (Test-Path $jarPath) { Remove-Item -Force $jarPath }
[System.IO.Compression.ZipFile]::CreateFromDirectory("$Build\classes", $jarPath)
& "$Bt\d8.bat" --lib $AndroidJar --min-api 21 --output "$Build\dex" $jarPath
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/6] package + zipalign"
$zip = [System.IO.Compression.ZipFile]::Open("$Build\base.apk", "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$Build\dex\classes.dex", "classes.dex", "Optimal") | Out-Null
$zip.Dispose()
& "$Bt\zipalign.exe" -f -p 4 "$Build\base.apk" "$Build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/6] sign"
$outApk = Join-Path $Proj $OutName
if (-not $KsPass -or -not $KeyPass) {
    Copy-Item -Force "$Build\aligned.apk" $outApk
    Write-Host ""
    Write-Host "未提供签名口令，已输出未签名 APK：$outApk"
    Write-Host "如需签名，请先设置环境变量 CANDY_KS_PASS / CANDY_KEY_PASS"
    exit 0
}

if (-not $KsPath) { $KsPath = Join-Path $Proj "keystore\candy.jks" }
if (-not (Test-Path $KsPath)) {
    Write-Host ""
    Write-Host "签名文件不存在：$KsPath"
    Write-Host "可用 keytool 自行生成（示例）："
    Write-Host '  keytool -genkeypair -keystore my.jks -alias candy -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Your Name,O=YourOrg,C=CN"'
    Copy-Item -Force "$Build\aligned.apk" $outApk
    Write-Host "已输出未签名 APK：$outApk"
    exit 0
}

& "$Bt\apksigner.bat" sign --ks $KsPath --ks-key-alias $KsAlias `
    --ks-pass "pass:$KsPass" --key-pass "pass:$KeyPass" `
    --v1-signing-enabled true --v2-signing-enabled true `
    --out $outApk "$Build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& "$Bt\apksigner.bat" verify --print-certs $outApk
Write-Host ""
Write-Host "OK -> $outApk"
