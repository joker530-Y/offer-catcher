$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mainClasses = Join-Path $root "build\classes"
$testClasses = Join-Path $root "build\test-classes"
$lib = Join-Path $root "lib"

powershell -ExecutionPolicy Bypass -File (Join-Path $root "scripts\build.ps1")

if (Test-Path $testClasses) {
  Remove-Item -LiteralPath $testClasses -Recurse -Force
}

New-Item -ItemType Directory -Path $testClasses | Out-Null
$sources = Get-ChildItem -Path (Join-Path $root "src\test\java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -cp "$mainClasses;$(Join-Path $lib '*')" -d $testClasses $sources
java -cp "$mainClasses;$testClasses;$(Join-Path $lib '*')" com.offercatcher.OfferCatcherSmokeTest
