# PowerShell script to compile the project and deploy it via WSL

Write-Host "=== Compilant el projecte amb Maven (Windows) ===" -ForegroundColor Cyan

# Navigate to project root
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

# Clean and build the JAR (same as run.ps1)
Write-Host "Netejant i compilant el JAR..." -ForegroundColor Yellow
mvn clean package -DskipTests=true

Write-Host "Verificant generació del JAR..." -ForegroundColor Yellow

# Check if JAR was created
$jarPath = "target\server-package.jar"

if (Test-Path $jarPath) {
    Write-Host "JAR generat correctament: $jarPath" -ForegroundColor Green
} else {
    Write-Host "Error: No s'ha pogut generar el JAR en $jarPath" -ForegroundColor Red
    exit 1
}

# Now call WSL to deploy the JAR
Write-Host "`n=== Desplegant al servidor via WSL ===" -ForegroundColor Cyan
Set-Location "$projectRoot\proxmox\Windows"

# Execute the bash deployment script via WSL
wsl bash ./proxmoxDeploy.sh

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n=== Desplegament completat amb èxit ===" -ForegroundColor Green
} else {
    Write-Host "`n=== Error durant el desplegament ===" -ForegroundColor Red
    exit 1
}
