@echo off
echo === AutoFarm Bootstrap (Windows) ===

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Install JDK 17+ from https://adoptium.net
    pause
    exit /b 1
)
java -version 2>&1 | findstr /i "version" && echo Java OK

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Downloading Gradle wrapper jar...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
    echo Wrapper jar downloaded.
)

echo.
echo Bootstrap complete! Now run:
echo   gradlew.bat :app-ui:run           -- Run the app
echo   gradlew.bat :app-ui:packageMsi    -- Build Windows installer
echo.
echo REMINDER: Set up IMAP catch-all email before using OTP steps.
echo See README.md for instructions.
pause
