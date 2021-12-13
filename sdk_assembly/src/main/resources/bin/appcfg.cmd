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

rem Launches the AppCfg utility, which allows Google App Engine
rem developers to deploy their application to the cloud.

echo 'The appcfg utility is deprecated. Please use the Google Cloud SDK' 1>&2
echo 'for App Engine development.' 1>&2
echo 'https://cloud.google.com/appengine/docs/standard/java/download' 1>&2

java -Xmx1100m -cp "%~dp0\..\lib\appengine-tools-api.jar" com.google.appengine.tools.admin.AppCfg %*
