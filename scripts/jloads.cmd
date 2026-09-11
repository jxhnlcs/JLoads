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

set "YTDLP_OK="
if exist "bin\yt-dlp.exe" set "YTDLP_OK=1"
where yt-dlp >nul 2>&1 && set "YTDLP_OK=1"
if not defined YTDLP_OK (
  echo.
  echo [JLoads] ATENCAO: o yt-dlp nao foi encontrado. Sem ele nao da para baixar.
  echo          Instale com: winget install yt-dlp.yt-dlp
  echo          Depois feche esta janela e abra o jloads.cmd novamente.
  echo.
)

set "FFMPEG_OK="
if exist "bin\ffmpeg.exe" set "FFMPEG_OK=1"
where ffmpeg >nul 2>&1 && set "FFMPEG_OK=1"
if not defined FFMPEG_OK (
  echo [JLoads] ATENCAO: o FFmpeg nao foi encontrado. Downloads em MP3 vao falhar.
  echo          Instale com: winget install Gyan.FFmpeg
  echo.
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
