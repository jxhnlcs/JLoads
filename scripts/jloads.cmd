@echo off
setlocal
cd /d "%~dp0"
title JLoads

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo [JLoads] Java 21 ou superior nao foi encontrado.
  echo          Instale com: winget install EclipseAdoptium.Temurin.21.JRE
  pause
  exit /b 1
)

set "JAR="
for %%F in (jloads-*.jar) do set "JAR=%%F"
if not defined JAR (
  echo [JLoads] Arquivo jloads-*.jar nao encontrado nesta pasta.
  pause
  exit /b 1
)

echo [JLoads] Iniciando. O navegador abre sozinho quando estiver pronto.
echo [JLoads] Para encerrar, feche esta janela ou pressione Ctrl+C.
"%JAVA_EXE%" -jar "%JAR%" --app.open-browser=true %*
if errorlevel 1 (
  echo.
  echo [JLoads] O JLoads foi encerrado com erro.
  echo          Se aparecer "UnsupportedClassVersionError", seu Java e antigo: instale o Java 21
  echo          com "winget install EclipseAdoptium.Temurin.21.JRE" e abra este arquivo novamente.
  pause
)
