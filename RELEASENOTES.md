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

# Release Notes 4.0.0 December 30 2025.

## Breaking Changes

* Package Renaming for Byte-Safe Protos:
The core change is the introduction of parallel Java proto libraries. 
When a .proto file is processed to handle potentially non-UTF8 string fields 
as bytes, the generated Java code is placed in a new package.
Typically, the java_package option in the .proto files is modified. 
For example, com.google.appengine.api.taskqueue becomes com.google.appengine.api.taskqueue_bytes.
Similarly, com.google.storage.onestore.v3 becomes com.google.storage.onestore.v3_bytes.
* bytes Instead of string:
The fundamental goal is to address fields in protos that were declared as 
string but in practice could contain arbitrary byte arrays, not necessarily valid UTF-8 strings. 
In Java Proto1, these were often treated as String, 
which could lead to corruption or exceptions.
*   The `com.google.appengine.api.datastore` package has been updated to use
    proto2 versions of Onestore Entity protos. These protos are repackaged
    (shaded) within this JAR.
*   The `com.google.appengine.api.datastore.EntityTranslator` class:
    *   `EntityTranslator.createFromPb(EntityProto)` now accepts
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.EntityProto`.
    *   `EntityTranslator.convertToPb(Entity)` now returns
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.EntityProto`.
*   The `com.google.appengine.api.datastore.KeyTranslator` class:
    *   `KeyTranslator.createFromPb(Reference)` now accepts
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.Reference`.
    *   `KeyTranslator.convertToPb(Key)` now returns
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.Reference`.
*   The `com.google.appengine.api.datastore.DataTypeTranslator` class:
    *   Methods `addPropertiesToPb`, `extractPropertiesFromPb`,
        `extractIndexedPropertiesFromPb`, `extractImplicitPropertiesFromPb`, and
        `findIndexedPropertiesOnPb` now accept
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.EntityProto`.
    *   Method `getPropertyValue` now accepts
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Property`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.Property`.
    *   Method `getComparablePropertyValue` now accepts
        `com.google.appengine.repackaged.com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Property`
        instead of
        `com.google.storage.onestore.v3.OnestoreEntity.Property`.
