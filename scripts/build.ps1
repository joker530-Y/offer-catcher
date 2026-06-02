$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root "build\classes"
$lib = Join-Path $root "lib"
$pdfboxVersion = "3.0.3"
$pdfboxJar = Join-Path $lib "pdfbox-app-$pdfboxVersion.jar"

if (Test-Path $classes) {
  Remove-Item -LiteralPath $classes -Recurse -Force
}

New-Item -ItemType Directory -Path $classes | Out-Null
New-Item -ItemType Directory -Path $lib -Force | Out-Null

if (-not (Test-Path $pdfboxJar)) {
  $url = "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/$pdfboxVersion/pdfbox-app-$pdfboxVersion.jar"
  Write-Host "Downloading PDFBox $pdfboxVersion..."
  Invoke-WebRequest -Uri $url -OutFile $pdfboxJar
}

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -cp (Join-Path $lib "*") -d $classes $sources
Write-Host "Compiled Java classes to $classes"
