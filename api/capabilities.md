<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Google App Engine Capabilities Documentation

*   [Capabilities API for Bundled Services](#capabilities-api-for-bundled-services)
    *   [Using the Capabilities API](#using-the-capabilities-api)
    *   [Supported Capabilities](#supported-capabilities)

## Capabilities API for Bundled Services

With the Capabilities API, your application can detect outages and scheduled
downtime for specific API capabilities. You can use this API to reduce downtime
in your application by detecting when a capability is unavailable and then
bypassing it.

**Note:** Every status request to this API always returns ENABLED except for the
"Datastore writes" capability, which returns DISABLED if Datastore is in
read-only mode for your app.

### Using the Capabilities API

The `CapabilitySet` class defines all of the available methods for this API. You
can either name capabilities explicitly or infer them from the methods provided
by this class.

### Supported Capabilities

Capability                             | Arguments to CapabilitySet
-------------------------------------- | --------------------------
Availability of the blobstore          | "blobstore"
Datastore reads                        | "datastore_v3"
Datastore writes                       | "datastore_v3", ["write"]
Availability of the Images service     | "images"
Availability of the Mail service       | "mail"
Availability of the Memcache service   | "memcache"
Availability of the Task Queue service | "taskqueue"
Availability of the URL Fetch service  | "urlfetch"
