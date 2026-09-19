@echo off
REM Bake the common-j8 shared jar first: it is built by the root Gradle 7.6 with JDK 17.
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.1
pushd "%~dp0..\.."
call gradlew.bat :common-j8:jar --console=plain -q
set ODC_J8_RC=%ERRORLEVEL%
popd
if not "%ODC_J8_RC%"=="0" exit /b %ODC_J8_RC%
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_181
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java:
java -version
echo.
echo Building with Gradle 2.14...
gradlew.bat build --no-daemon