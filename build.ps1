param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = Join-Path $PSScriptRoot '.tools\jdk'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools\gradle-cache'
if (!(Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Project JDK missing. Install JDK 17 or adjust JAVA_HOME in this script.' }
Push-Location $PSScriptRoot
try {
    & '.\gradlew.bat' @Tasks --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
