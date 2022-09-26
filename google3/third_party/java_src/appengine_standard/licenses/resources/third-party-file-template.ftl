<#--
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

<#-- Format artifact "name (groupId:artifactId:version - url)" -->
<#function artifactFormat artifact>
    <#if artifact.name?index_of('Unnamed') &gt; -1>
        <#return artifact.artifactId + " (" + artifact.groupId + ":" + artifact.artifactId + ":" + artifact.version + " - " + (artifact.url!"no url defined") + ")">
    <#else>
        <#return artifact.name + " (" + artifact.groupId + ":" + artifact.artifactId + ":" + artifact.version + " - " + (artifact.url!"no url defined") + ")">
    </#if>
</#function>

<#-- Create a key from provided licenses list, ordered alphabetically: "license A, license B, license C" -->
<#function licensesKey licenses>
  <#local result = "">
  <#list licenses?sort as license>
      <#local result=result + ", " + license>
  </#list>
  <#return result?substring(2)>
</#function>

<#-- Aggregate dependencies map for generated license key (support for multi-license) and convert artifacto to string -->
<#function aggregateLicenses dependencies>
    <#assign aggregate = {}>
    <#list dependencyMap as entry>
        <#assign project = artifactFormat(entry.getKey())/>
        <#assign licenses = entry.getValue()/>
        <#assign key = licensesKey(licenses)/>
        <#if aggregate[key]?? >
            <#assign replacement = aggregate[key] + [project] />
            <#assign aggregate = aggregate + {key:replacement} />
        <#else>
          <#assign aggregate = aggregate + {key:[project]} />
        </#if>
    </#list>
    <#return aggregate>
</#function>

The repository contains 3rd-party code under the following licenses:

<#if dependencyMap?size == 0>
  The project has no dependencies.
<#else>
<#assign aggregate = aggregateLicenses(dependencyMap)>
    <#-- Print sorted aggregate licenses -->
    <#list aggregate?keys?sort as licenses>
        <#assign projects = aggregate[licenses]/>

    ${licenses}

        <#-- Print sorded projects -->
        <#list projects?sort as project>
        * ${project}
        </#list>
    </#list>
</#if>
