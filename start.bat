@echo off
echo.
echo  =====================================================
echo   Smart Segregation System - Starting...
echo  =====================================================
echo.

REM Step 1: Compile Java
echo  [1/2] Compiling Java server...
javac -cp ".;mysql-connector-j-8.0.33.jar" SmartSegServer.java
if %errorlevel% neq 0 (
    echo.
    echo  ERROR: Java compilation failed!
    echo  Make sure Java 21 is installed. Run: java -version
    pause
    exit /b 1
)
echo  Compilation successful!
echo.

REM Step 2: Start Java server
echo  [2/2] Starting Java server...
echo  Opening browser at http://localhost:3000/login.html
echo.
start http://localhost:3000/login.html
java -cp ".;mysql-connector-j-8.0.33.jar" SmartSegServer
pause
