# Baut die signierte Release-APK und veroeffentlicht sie als GitHub-Release.
# Die installierte App findet das Release beim naechsten Start (bzw. Einstellungen -> Nach Updates suchen).
#
# Vorher: versionCode (+1) und versionName in app\build.gradle.kts erhoehen, committen und pushen.
# Aufruf: powershell -ExecutionPolicy Bypass -File scripts\publish-release.ps1 [-Notes "Was ist neu"]
# Ziel-Repo ist UPDATE_REPO aus app\build.gradle.kts (muss oeffentlich sein, gh muss angemeldet sein).
param([string]$Notes = '')
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

$gradle = Get-Content 'app\build.gradle.kts' -Raw
if ($gradle -notmatch 'versionName = "(\d+\.\d+\.\d+)"') { throw 'versionName (x.y.z) fehlt in app\build.gradle.kts' }
$version = $Matches[1]
if ($gradle -notmatch '"UPDATE_REPO", "\\"([^"\]+)\\""') { throw 'UPDATE_REPO fehlt in app\build.gradle.kts' }
$repo = $Matches[1]
if (-not (Test-Path '..\vorrat-signing\keystore.properties')) { throw 'Signatur fehlt: ..\vorrat-signing\keystore.properties' }

# Nur saubere, gepushte Staende veroeffentlichen: das Release-Tag zeigt auf genau diesen Commit.
if (git status --porcelain) { throw 'Es gibt nicht committete Aenderungen.' }
git fetch --quiet origin
$head = (git rev-parse HEAD).Trim()
if (-not (git branch -r --contains $head)) { throw "Commit $head ist noch nicht gepusht (git push)." }

$ErrorActionPreference = 'Continue'
gh release view "v$version" --repo $repo *> $null
if ($LASTEXITCODE -eq 0) { throw "Release v$version gibt es schon: versionCode und versionName erhoehen." }
$ErrorActionPreference = 'Stop'

& .\gradlew.bat --quiet clean assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Build fehlgeschlagen' }
$built = 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $built)) { throw "$built fehlt (unsigniert gebaut?)" }
New-Item -ItemType Directory -Force dist | Out-Null
$apk = "dist\vorrat-$version.apk"
Copy-Item $built $apk -Force

if (-not $Notes) { $Notes = "Vorrat $version" }
$ErrorActionPreference = 'Continue'
gh release create "v$version" $apk --repo $repo --target $head --title "Vorrat $version" --notes $Notes
if ($LASTEXITCODE -ne 0) { throw 'gh release create fehlgeschlagen' }
Write-Output "Veroeffentlicht: v$version in $repo ($apk)"
