@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  LegalCheckBackend startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables, and ensure extensions are enabled
setlocal EnableExtensions

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and LEGAL_CHECK_BACKEND_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\LegalCheckBackend-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\exposed-dao-0.56.0.jar;%APP_HOME%\lib\exposed-jdbc-0.56.0.jar;%APP_HOME%\lib\exposed-java-time-0.56.0.jar;%APP_HOME%\lib\exposed-core-0.56.0.jar;%APP_HOME%\lib\dotenv-kotlin-6.5.1.jar;%APP_HOME%\lib\ktor-serialization-kotlinx-json-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-content-negotiation-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-netty-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-auth-jwt-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-auth-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-status-pages-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-sessions-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-server-core-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-client-cio-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-client-content-negotiation-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-client-core-jvm-3.5.0.jar;%APP_HOME%\lib\kotlin-reflect-2.3.21.jar;%APP_HOME%\lib\ktor-serialization-kotlinx-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-http-cio-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-websocket-serialization-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-serialization-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-websockets-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-network-tls-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-http-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-events-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-sse-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-network-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-utils-jvm-3.5.0.jar;%APP_HOME%\lib\ktor-io-jvm-3.5.0.jar;%APP_HOME%\lib\kotlinx-coroutines-core-jvm-1.11.0.jar;%APP_HOME%\lib\kotlinx-coroutines-slf4j-1.11.0.jar;%APP_HOME%\lib\kotlinx-serialization-core-jvm-1.11.0.jar;%APP_HOME%\lib\kotlinx-serialization-json-io-jvm-1.11.0.jar;%APP_HOME%\lib\kotlinx-serialization-json-jvm-1.11.0.jar;%APP_HOME%\lib\kotlinx-io-core-jvm-0.9.0.jar;%APP_HOME%\lib\kotlinx-io-bytestring-jvm-0.9.0.jar;%APP_HOME%\lib\kotlin-stdlib-2.4.0.jar;%APP_HOME%\lib\logback-classic-1.5.37.jar;%APP_HOME%\lib\mysql-connector-j-9.0.0.jar;%APP_HOME%\lib\HikariCP-5.1.0.jar;%APP_HOME%\lib\bcrypt-0.10.2.jar;%APP_HOME%\lib\flyway-mysql-10.10.0.jar;%APP_HOME%\lib\flyway-core-10.10.0.jar;%APP_HOME%\lib\openpdf-1.3.30.jar;%APP_HOME%\lib\javase-3.5.3.jar;%APP_HOME%\lib\core-3.5.3.jar;%APP_HOME%\lib\annotations-23.0.0.jar;%APP_HOME%\lib\logback-core-1.5.37.jar;%APP_HOME%\lib\slf4j-api-2.0.18.jar;%APP_HOME%\lib\protobuf-java-4.26.1.jar;%APP_HOME%\lib\bytes-1.5.0.jar;%APP_HOME%\lib\dotenv-java-3.2.0.jar;%APP_HOME%\lib\java-jwt-4.5.2.jar;%APP_HOME%\lib\jwks-rsa-0.24.0.jar;%APP_HOME%\lib\jackson-databind-2.21.3.jar;%APP_HOME%\lib\jackson-core-2.21.3.jar;%APP_HOME%\lib\jackson-dataformat-toml-2.21.3.jar;%APP_HOME%\lib\gson-2.10.1.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\jcommander-1.82.jar;%APP_HOME%\lib\jai-imageio-core-1.4.0.jar;%APP_HOME%\lib\config-1.4.8.jar;%APP_HOME%\lib\netty-codec-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-http2-4.2.13.Final.jar;%APP_HOME%\lib\alpn-api-1.1.3.v20160715.jar;%APP_HOME%\lib\netty-transport-native-kqueue-4.2.13.Final.jar;%APP_HOME%\lib\netty-transport-native-epoll-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-http-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-compression-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-protobuf-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-marshalling-4.2.13.Final.jar;%APP_HOME%\lib\netty-handler-4.2.13.Final.jar;%APP_HOME%\lib\netty-codec-base-4.2.13.Final.jar;%APP_HOME%\lib\netty-transport-classes-kqueue-4.2.13.Final.jar;%APP_HOME%\lib\netty-transport-classes-epoll-4.2.13.Final.jar;%APP_HOME%\lib\netty-transport-native-unix-common-4.2.13.Final.jar;%APP_HOME%\lib\netty-transport-4.2.13.Final.jar;%APP_HOME%\lib\netty-buffer-4.2.13.Final.jar;%APP_HOME%\lib\netty-resolver-4.2.13.Final.jar;%APP_HOME%\lib\netty-common-4.2.13.Final.jar;%APP_HOME%\lib\guava-33.6.0-jre.jar;%APP_HOME%\lib\jackson-annotations-2.21.jar;%APP_HOME%\lib\failureaccess-1.0.3.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jspecify-1.0.0.jar;%APP_HOME%\lib\error_prone_annotations-2.47.0.jar;%APP_HOME%\lib\j2objc-annotations-3.1.jar


@rem Execute LegalCheckBackend
@rem endlocal doesn't take effect until after the line is parsed and variables are expanded
@rem which allows us to clear the local environment before executing the java command
endlocal & "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %LEGAL_CHECK_BACKEND_OPTS%  -classpath "%CLASSPATH%" com.example.MainKt %* & call :exitWithErrorLevel

:exitWithErrorLevel
@rem Use "%COMSPEC%" /c exit to allow operators to work properly in scripts
"%COMSPEC%" /c exit %ERRORLEVEL%
