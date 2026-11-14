@echo off
REM ============================================================
REM  耳机控制器 - 构建脚本
REM  前置条件：JDK 17 + Android SDK（见 README.md）
REM  用法：build.bat         构建 release（默认）
REM        build.bat debug   构建 debug
REM ============================================================
setlocal

if "%ANDROID_HOME%"=="" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    ) else (
        echo [ERROR] 未找到 Android SDK，请设置 ANDROID_HOME。
        exit /b 1
    )
)

if not exist "%ANDROID_HOME%\local.properties" (
    echo sdk.dir=%ANDROID_HOME:\=/%> local.properties
)

set "VARIANT=release"
if /i "%~1"=="debug" set "VARIANT=debug"

echo [INFO] ANDROID_HOME=%ANDROID_HOME%
echo [INFO] 构建变体：%VARIANT%
call gradlew.bat :app:assemble%VARIANT%
if errorlevel 1 (
    echo [ERROR] 构建失败。
    exit /b 1
)

echo.
echo [OK] APK 输出：
if /i "%VARIANT%"=="debug" (
    echo     app\build\outputs\apk\debug\app-debug.apk
) else (
    echo     app\build\outputs\apk\release\app-release.apk
)
echo     安装名：debug=「我的X5s Pro (Debug)」 release=「我的X5s Pro」
endlocal
