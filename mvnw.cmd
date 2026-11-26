@echo off
setlocal
set MAVEN_VERSION=3.10.2
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading Apache Maven %MAVEN_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $base='%USERPROFILE%\.m2\wrapper\dists'; New-Item -ItemType Directory -Force -Path $base ^| Out-Null; $zip=Join-Path $env:TEMP 'apache-maven-%MAVEN_VERSION%-bin.zip'; $url='%MAVEN_URL%'; Invoke-WebRequest -Uri $url -OutFile $zip; $expected=((Invoke-WebRequest -Uri ($url + '.sha512')).Content -split '\s+')[0]; $actual=(Get-FileHash -Algorithm SHA512 $zip).Hash; if ($actual -ne $expected) { throw 'Maven checksum verification failed' }; Expand-Archive -Force $zip $base; Remove-Item $zip"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
