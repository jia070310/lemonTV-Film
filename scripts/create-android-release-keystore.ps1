#Requires -Version 5.1
<#
.SYNOPSIS
  Generate android/lemon-release.jks and android/keystore.properties (gitignored) for assembleRelease.
  Backup the .jks and passwords; losing them prevents signing updates with the same key.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $root 'android'
$ks = Join-Path $androidDir 'lemon-release.jks'
$props = Join-Path $androidDir 'keystore.properties'
$alias = 'lemon_release'

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
  if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  }
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
  Write-Error 'keytool not found. Install JDK 17 and set JAVA_HOME.'
}

$chars = [char[]]([char]'A'..[char]'Z') + [char[]]([char]'a'..[char]'z') + [char[]]([char]'0'..[char]'9')
$pwd = -join (1..32 | ForEach-Object { $chars | Get-Random })

if (Test-Path -LiteralPath $ks) {
  Write-Host "Keystore already exists: $ks (not overwritten)"
  if (-not (Test-Path -LiteralPath $props)) {
    Write-Error "Missing $props. Restore from backup or copy keystore.properties.example and fill passwords for this .jks."
  }
  exit 0
}

$dname = 'CN=Lemon TV, OU=Release, O=Lemon TV, L=CN, ST=CN, C=CN'
& keytool @(
  '-genkeypair', '-v',
  '-keystore', $ks,
  '-alias', $alias,
  '-keyalg', 'RSA',
  '-keysize', '2048',
  '-validity', '10000',
  '-storepass', $pwd,
  '-keypass', $pwd,
  '-dname', $dname
)
Write-Host "Created: $ks"

$lines = @(
  ('storePassword=' + $pwd),
  ('keyPassword=' + $pwd),
  ('keyAlias=' + $alias),
  'storeFile=lemon-release.jks',
  ''
)
Set-Content -LiteralPath $props -Value $lines -Encoding utf8
Write-Host "Wrote: $props (do not commit)"
Write-Host 'Next: npm run build; npx cap sync android; cd android; .\gradlew.bat assembleRelease'
