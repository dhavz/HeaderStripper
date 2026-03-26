@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "SRC_DIR=%ROOT_DIR%\src\main\java"
set "BUILD_DIR=%ROOT_DIR%\build"
set "CLASSES_DIR=%BUILD_DIR%\classes"
set "DIST_DIR=%ROOT_DIR%\dist"

if defined BURP_API_JAR (
  set "API_JAR=%BURP_API_JAR%"
) else (
  set "API_JAR=%ROOT_DIR%\lib\burp-extender-api.jar"
)

if not exist "%API_JAR%" (
  echo Burp Extender API JAR not found.
  echo Set BURP_API_JAR=path\to\burp-extender-api.jar or place the file at lib\burp-extender-api.jar
  exit /b 1
)

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%CLASSES_DIR%"
mkdir "%DIST_DIR%"

set "SOURCES_FILE=%BUILD_DIR%\sources.txt"
for /r "%SRC_DIR%" %%f in (*.java) do echo %%f>>"%SOURCES_FILE%"

javac --release 8 -cp "%API_JAR%" -d "%CLASSES_DIR%" @"%SOURCES_FILE%"
if errorlevel 1 exit /b 1

jar cf "%DIST_DIR%\header-stripper-burp-extension.jar" -C "%CLASSES_DIR%" .
if errorlevel 1 exit /b 1

echo Built:
echo   %DIST_DIR%\header-stripper-burp-extension.jar
