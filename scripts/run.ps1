$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$env:STATIC_DIR = Join-Path $root "src\main\resources\static"
if (-not $env:PORT) {
  $env:PORT = "8080"
}

$classes = Join-Path $root "build\classes"
$lib = Join-Path $root "lib\*"
java -cp "$classes;$lib" com.offercatcher.OfferCatcherApplication
