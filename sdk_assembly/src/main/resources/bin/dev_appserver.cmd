@REM
@REM Copyright 2021 Google LLC
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off

rem Launches the Development AppServer.  This utility allows developers
rem to test a Google App Engine application on their local workstation.

echo 'The dev_appserver shipped with the standalone Java SDK is deprecated.' 1>&2
echo 'Please use the java_dev_appserver.sh shipped with the Cloud SDK instead.' 1>&2
echo 'https://cloud.google.com/appengine/docs/standard/java/download' 1>&2

java -cp "%~dp0\..\lib\appengine-tools-api.jar" ^
    com.google.appengine.tools.KickStart ^
       com.google.appengine.tools.development.DevAppServerMain %*

