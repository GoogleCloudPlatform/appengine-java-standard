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

# Google App Engine Datastore API Documentation

*   [Overview](#overview)
*   [Core Concepts](#core-concepts)
    *   [Entities](#entities)
    *   [Entity Keys](#entity-keys)
    *   [Entity Groups and Ancestor Paths](#entity-groups-and-ancestor-paths)
*   [Supported Data Types](#supported-data-types)
*   [Basic Operations](#basic-operations)
    *   [Initialize DatastoreService](#initialize-datastoreservice)
    *   [Create Entity](#create-entity)
    *   [Retrieve Entity](#retrieve-entity)
    *   [Update Entity](#update-entity)
    *   [Delete Entity](#delete-entity)
    *   [Batch Operations](#batch-operations)
*   [Working with Properties](#working-with-properties)
    *   [Repeated Properties (Multi-valued)](#repeated-properties-multi-valued)
    *   [Embedded Entities](#embedded-entities)
    *   [Empty List Support](#empty-list-support)
*   [Queries](#queries)
    *   [Basic Query Structure](#basic-query-structure)
    *   [Property Filters](#property-filters)
    *   [Composite Filters](#composite-filters)
    *   [Key Filters](#key-filters)
    *   [Ancestor Queries](#ancestor-queries)
    *   [Sort Orders](#sort-orders)
    *   [Special Query Types](#special-query-types)
        *   [Keys-Only Query](#keys-only-query)
        *   [Kindless Query](#kindless-query)
        *   [Kindless Ancestor Query](#kindless-ancestor-query)
*   [Retrieving Results](#retrieving-results)
    *   [Single Entity](#single-entity)
    *   [Iterating Results](#iterating-results)
    *   [With Limit](#with-limit)
    *   [Chunk Size](#chunk-size)
*   [Query Cursors](#query-cursors)
    *   [Using Cursors](#using-cursors)
    *   [Cursor Limitations](#cursor-limitations)
*   [Generating Keys](#generating-keys)
    *   [Simple Key](#simple-key)
    *   [Key with Ancestor Path](#key-with-ancestor-path)
    *   [String Representation](#string-representation)
*   [Transactions](#transactions)
    *   [Transaction Rules](#transaction-rules)
    *   [Cross-Group (XG) Transactions](#cross-group-xg-transactions)
    *   [Consistency Guarantees](#consistency-guarantees)
*   [Indexes](#indexes)
    *   [Automatic Indexes](#automatic-indexes)
    *   [Custom Indexes (index.yaml)](#custom-indexes-indexyaml)
    *   [Index Configuration Elements](#index-configuration-elements)
    *   [Creating Index Files](#creating-index-files)
*   [Best Practices](#best-practices)
    *   [Entity Design](#entity-design)
    *   [Query Optimization](#query-optimization)
    *   [Transaction Guidelines](#transaction-guidelines)
    *   [Cost Management](#cost-management)
*   [Error Handling](#error-handling)
    *   [Common Exceptions](#common-exceptions)
    *   [Transaction Errors](#transaction-errors)
*   [Additional Features](#additional-features)
    *   [Statistics Overview](#statistics-overview)
    *   [API Packages](#api-packages)
*   [Data Consistency](#data-consistency)
    *   [Consistency Levels](#consistency-levels)
    *   [Setting Read Policy](#setting-read-policy)
    *   [Consistency Guidelines](#consistency-guidelines)
*   [Query Restrictions](#query-restrictions)
    *   [Entity Property Requirements](#entity-property-requirements)
    *   [Unindexed Properties](#unindexed-properties)
    *   [Inequality Filter Restrictions](#inequality-filter-restrictions)
    *   [Sort Order Restrictions](#sort-order-restrictions)
    *   [Multi-Valued Property Behavior](#multi-valued-property-behavior)
    *   [Transaction Query Requirements](#transaction-query-requirements)
*   [Projection Queries](#projection-queries)
    *   [Basic Usage](#basic-usage)
    *   [Grouping (Experimental)](#grouping-experimental)
    *   [Projection Limitations](#projection-limitations)
    *   [Multi-Valued Properties](#multi-valued-properties)
    *   [Index Requirements](#index-requirements)
*   [Advanced Index Management](#advanced-index-management)
    *   [Exploding Indexes](#exploding-indexes)
    *   [Index Limits](#index-limits)
    *   [Unindexed Properties](#unindexed-properties)
*   [Async Datastore API](#async-datastore-api)
    *   [Basic Usage](#basic-usage)
    *   [Async Transactions](#async-transactions)
    *   [Async Queries](#async-queries)
    *   [When to Use Async](#when-to-use-async)
    *   [Future Considerations](#future-considerations)
*   [Datastore Callbacks](#datastore-callbacks)
    *   [Callback Types](#callback-types)
    *   [Callback Requirements](#callback-requirements)
    *   [Batch Operations](#batch-operations)
    *   [Async Interaction](#async-interaction)
    *   [Common Errors to Avoid](#common-errors-to-avoid)
*   [Metadata Queries](#metadata-queries)
    *   [Special Entity Kinds](#special-entity-kinds)
    *   [Entity Group Metadata](#entity-group-metadata)
    *   [Namespace Queries](#namespace-queries)
    *   [Kind Queries](#kind-queries)
    *   [Property Queries](#property-queries)
    *   [Metadata Performance](#metadata-performance)
*   [Datastore Statistics](#datastore-statistics)
    *   [Available Statistics](#available-statistics)
    *   [Common Properties](#common-properties)
    *   [Usage Example](#usage-example)
    *   [Best Practice](#best-practice)
    *   [Statistics Pruning](#statistics-pruning)
*   [Structuring for Strong Consistency](#structuring-for-strong-consistency)
    *   [The Trade-off](#the-trade-off)
    *   [Design Patterns](#design-patterns)
    *   [When Heavy Write Usage Expected](#when-heavy-write-usage-expected)

## Overview

This guide covers the App Engine Datastore API for Java, which provides access
to Google Cloud Firestore in Datastore mode (formerly Google Cloud Datastore).

## Core Concepts

### Entities

Entities are data objects stored in Datastore with the following
characteristics:

-   **Schemaless**: No enforced structure or property requirements
-   **Kind**: Categories entities for query purposes (e.g., "Employee",
    "Person")
-   **Properties**: Named values with various data types
-   **Key**: Unique identifier consisting of:
    -   Namespace (for multitenancy)
    -   Kind
    -   Identifier (key name string or numeric ID)
    -   Optional ancestor path

### Entity Keys

Each entity has a unique key with components:

```java
// Key with custom name
Entity employee = new Entity("Employee", "asalieri");

// Key with auto-generated numeric ID
Entity employee = new Entity("Employee");
```

Key structure for entities with ancestors: `[Person:GreatGrandpa,
Person:Grandpa, Person:Dad, Person:Me]`

### Entity Groups and Ancestor Paths

-   **Root Entity**: Entity without a parent
-   **Child Entity**: Entity with a designated parent
-   **Entity Group**: Root entity and all descendants
-   **Ancestor Path**: Sequence from root to specific entity

Creating entities with ancestors:

```java
Entity("Employee"); datastore.put(employee);

Entity address = new Entity("Address", employee.getKey());
datastore.put(address);

// With key name Entity address = new Entity("Address", "addr1",
employee.getKey());
```

## Supported Data Types

| Value Type           | Java Type(s)                                        |
| -------------------- | --------------------------------------------------- |
| Integer              | `short`, `int`, `long`, `java.lang.Short`, `java.lang.Integer`, `java.lang.Long` |
| Floating-point       | `float`, `double`, `java.lang.Float`, `java.lang.Double`  |
| Boolean              | `boolean`, `java.lang.Boolean`                      |
| Text string (short)  | `java.lang.String` (up to 1500 bytes, indexed)      |
| Text string (long)   | `com.google.appengine.api.datastore.Text` (up to 1MB, not indexed)   |
| Byte string (short)  | `com.google.appengine.api.datastore.ShortBlob`      |
| Byte string (long)   | `com.google.appengine.api.datastore.Blob`           |
| Date and time        | `java.util.Date`                                    |
| Geographical point   | `com.google.appengine.api.datastore.GeoPt`          |
| Postal address       | `com.google.appengine.api.datastore.PostalAddress`  |
| Telephone number     | `com.google.appengine.api.datastore.PhoneNumber`    |
| Email address        | `com.google.appengine.api.datastore.Email`          |
| Google Accounts user | `com.google.appengine.api.users.User`               |
| Datastore key        | `com.google.appengine.api.datastore.Key`            |
| Blobstore key        | `com.google.appengine.api.blobstore.BlobKey`        |
| Embedded entity      | `com.google.appengine.api.datastore.EmbeddedEntity` |
| Null                 | `null`                                              |

## Basic Operations

### Initialize DatastoreService

```java
DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
```

### Create Entity

```java
Entity employee = new Entity("Employee", "asalieri");
employee.setProperty("firstName", "Antonio");
employee.setProperty("lastName", "Salieri");
employee.setProperty("hireDate", new Date());
employee.setProperty("attendedHrTraining", true);

datastore.put(employee);
```

### Retrieve Entity

```java
// Key employeeKey = ...;
Entity employee = datastore.get(employeeKey);
```

### Update Entity

```java
Entity employee = new Entity("Employee", "asalieri");
employee.setProperty("firstName", "Antonio");
employee.setProperty("lastName", "Salieri");
employee.setProperty("hireDate", new Date());
employee.setProperty("attendedHrTraining", true);

datastore.put(employee);
```

**Note**: `put()` doesn't distinguish between create and update. It overwrites
if the key exists.

### Delete Entity

```java
// Key employeeKey = ...;
datastore.delete(employeeKey);
```

### Batch Operations

```java
Entity employee1 = new Entity("Employee");
Entity employee2 = new Entity("Employee");
Entity employee3 = new Entity("Employee");

List<Entity> employees = Arrays.asList(employee1, employee2, employee3);
datastore.put(employees);
```

Batch operations: - Group entities by entity group - Execute in parallel per
entity group - Faster than individual calls - May partially succeed (use
transactions for atomicity)

## Working with Properties

### Repeated Properties (Multi-valued)

```java
Entity employee = new Entity("Employee");
ArrayList<String> favoriteFruit = new ArrayList<String>();
favoriteFruit.add("Pear");
favoriteFruit.add("Apple");
employee.setProperty("favoriteFruit", favoriteFruit);
datastore.put(employee);

// Retrieve
employee = datastore.get(employee.getKey());
@SuppressWarnings("unchecked")
ArrayList<String> retrievedFruits = (ArrayList<String>) employee.getProperty("favoriteFruit");
```

### Embedded Entities

```java
EmbeddedEntity embeddedContactInfo = new EmbeddedEntity();
embeddedContactInfo.setProperty("homeAddress", "123 Fake St, Made, UP 45678");
embeddedContactInfo.setProperty("phoneNumber", "555-555-5555");
embeddedContactInfo.setProperty("emailAddress", "test@example.com");
employee.setProperty("contactInfo", embeddedContactInfo);
```

Properties of embedded entities: - Not indexed - Cannot be used in queries - Can
optionally have a key (but cannot be retrieved by it)

### Empty List Support

By default, empty collections are stored as `null`. To enable empty list
support:

```java
System.setProperty(DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT, Boolean.TRUE.toString());
```

**Warning**: Enabling empty list support changes query behavior since empty
lists aren't indexed but nulls are.

## Queries

### Basic Query Structure

```java
Query q = new Query("Person");
PreparedQuery pq = datastore.prepare(q);
```

A query includes: - Entity kind (optional for kindless queries) - Zero or more
filters - Zero or more sort orders

### Property Filters

```java
Filter heightMinFilter =
    new FilterPredicate("height", FilterOperator.GREATER_THAN_OR_EQUAL, minHeight);
Query q = new Query("Person").setFilter(heightMinFilter);
```

**Filter Operators:** - `EQUAL` - `LESS_THAN` - `LESS_THAN_OR_EQUAL` -
`GREATER_THAN` - `GREATER_THAN_OR_EQUAL` - `NOT_EQUAL` (performs 2 queries) -
`IN` (performs multiple queries)

### Composite Filters

```java
Filter heightMinFilter =
    new FilterPredicate("height", FilterOperator.GREATER_THAN_OR_EQUAL, minHeight);
Filter heightMaxFilter =
    new FilterPredicate("height", FilterOperator.LESS_THAN_OR_EQUAL, maxHeight);

CompositeFilter heightRangeFilter =
    CompositeFilterOperator.and(heightMinFilter, heightMaxFilter);

Query q = new Query("Person").setFilter(heightRangeFilter);
PreparedQuery pq = datastore.prepare(q);
```

### Key Filters

```java
Filter keyFilter =
    new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.GREATER_THAN, lastSeenKey);
Query q = new Query("Person").setFilter(keyFilter);
```

### Ancestor Queries

```java
Query q = new Query("Person").setAncestor(ancestorKey);
```

**Benefits:** - Strongly consistent results - Guaranteed up-to-date data -
Required for queries within transactions

### Sort Orders

```java
// Single sort
Query q = new Query("Person").addSort("lastName", SortDirection.ASCENDING);

// Multiple sorts
Query q = new Query("Person")
    .addSort("lastName", SortDirection.ASCENDING)
    .addSort("height", SortDirection.DESCENDING);
```

**Important**: If using inequality filters, the filtered property must be sorted
first.

### Special Query Types

#### Keys-Only Query

```java
Query q = new Query("Person").setKeysOnly();
```

Returns only entity keys, lower latency and cost.

#### Kindless Query

```java
Query q = new Query();
```

Retrieves all entities (no kind specified). Can only filter on keys, not
properties.

#### Kindless Ancestor Query

```java
Filter keyFilter =
    new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.GREATER_THAN, tomKey);
Query mediaQuery = new Query().setAncestor(tomKey).setFilter(keyFilter);
```

Returns ancestor and all descendants regardless of kind.

## Retrieving Results

### Single Entity

```java
Query q = new Query("Person")
    .setFilter(new FilterPredicate("lastName", FilterOperator.EQUAL, targetLastName));
PreparedQuery pq = datastore.prepare(q);
Entity result = pq.asSingleEntity();
```

Throws `TooManyResultsException` if multiple results found.

### Iterating Results

```java
PreparedQuery pq = datastore.prepare(q);
for (Entity result : pq.asIterable()) {
    String firstName = (String) result.getProperty("firstName");
    String lastName = (String) result.getProperty("lastName");
}
```

### With Limit

```java
Query q = new Query("Person").addSort("height", SortDirection.DESCENDING);
PreparedQuery pq = datastore.prepare(q);
List<Entity> results = pq.asList(FetchOptions.Builder.withLimit(5));
```

### Chunk Size

```java
FetchOptions options = FetchOptions.Builder.withChunkSize(20);
```

Default is 20 results per batch.

## Query Cursors

Cursors enable pagination without offsets (which are less efficient).

### Using Cursors

```java
FetchOptions fetchOptions = FetchOptions.Builder.withLimit(PAGE_SIZE);

// Use cursor if provided
String startCursor = req.getParameter("cursor");
if (startCursor != null) {
    fetchOptions.startCursor(Cursor.fromWebSafeString(startCursor));
}

Query q = new Query("Person").addSort("name", SortDirection.ASCENDING);
PreparedQuery pq = datastore.prepare(q);

QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);

// Get cursor for next page
String cursorString = results.getCursor().toWebSafeString();
```

### Cursor Limitations

-   Must reconstitute exact original query to use cursor
-   Not supported with `NOT_EQUAL`, `IN`, or composite OR queries
-   May not work correctly with inequality filters on multi-valued properties
-   Can be invalidated by App Engine implementation changes
-   Not encrypted (can be decoded to see entity information)

## Generating Keys

### Simple Key

```java
// With key name
Key k1 = KeyFactory.createKey("Person", "GreatGrandpa");

// With numeric ID
Key k2 = KeyFactory.createKey("Person", 74219);
```

### Key with Ancestor Path

```java
Key k = new KeyFactory.Builder("Person", "GreatGrandpa")
    .addChild("Person", "Grandpa")
    .addChild("Person", "Dad")
    .addChild("Person", "Me")
    .getKey();
```

### String Representation

```java
// Convert key to web-safe string
String personKeyStr = KeyFactory.keyToString(k);

// Reconstruct key from string
Key personKey = KeyFactory.stringToKey(personKeyStr);
Entity person = datastore.get(personKey);
```

**Note**: Key strings are web-safe but not encrypted. Users can decode them to
see application ID, kind, and identifiers.

## Transactions

Every create, update, or delete operation occurs within a transaction context.

### Transaction Rules

-   Maximum 25 entity groups per transaction
-   Only ancestor queries allowed within transactions
-   Write throughput: ~1 transaction/second per entity group
-   Uses optimistic concurrency control
-   First transaction to commit succeeds; others fail and can retry

### Cross-Group (XG) Transactions

-   Up to 25 entity groups
-   Can perform ancestor queries on separate entity groups
-   Cannot perform non-ancestor queries
-   May see partial results from previously committed XG transactions

### Consistency Guarantees

**Strongly Consistent (Ancestor Queries):** - Get operations by key - Ancestor
queries - Guaranteed up-to-date

**Eventually Consistent (Non-Ancestor Queries):** - May return slightly stale
data - Spans multiple entity groups - Results available after Apply phase
completes

## Indexes

### Automatic Indexes

Datastore automatically creates simple indexes on each property.

### Custom Indexes (index.yaml)

```yaml
indexes:
- kind: Cat
  ancestor: no
  properties:
  - name: name
  - name: age
    direction: desc

- kind: Store
  ancestor: yes
  properties:
  - name: business
    direction: asc
  - name: owner
    direction: asc
```

### Index Configuration Elements

-   `kind`: Entity kind (required)
-   `properties`: List of properties with optional direction
-   `ancestor`: `yes` for ancestor queries, default `no`

### Creating Index Files

**Automatically with Emulator:** `bash gcloud beta emulators datastore start
--data-dir DATA-DIR` Emulator auto-generates index definitions as you test
queries.

**Deploying Indexes:** `bash gcloud app deploy index.yaml`

**Cleaning Up Old Indexes:** `bash gcloud datastore indexes cleanup index.yaml`

## Best Practices

### Entity Design

1.  **Don't name properties "key"** - reserved for entity keys
2.  **Avoid storing `users.User` objects** - email addresses can change
3.  **Use entity groups strategically** - balance consistency needs vs. write
    throughput
4.  **Limit entity group size** - max ~1 write/second per group
5.  **Consider index costs** - limit to 20,000 indexed properties per entity

### Query Optimization

1.  **Use cursors instead of offsets** - offsets still retrieve skipped entities
2.  **Use keys-only queries** when you only need keys
3.  **Use projection queries** for specific properties only
4.  **Set appropriate limits** to control result size
5.  **Batch operations** when possible for better performance

### Transaction Guidelines

1.  **Keep transactions short** - reduce contention
2.  **Design for idempotency** - transactions may be retried
3.  **Use entity groups wisely** - for strongly related data
4.  **Avoid sensitive data in entity group keys** - may be retained after
    deletion

### Cost Management

1.  **Learn about write costs** before coding
2.  **Avoid unnecessary indexes** on properties
3.  **Use batch operations** to reduce service call overhead
4.  **Consider eventual consistency** when strong consistency isn't required
5.  **Delete unused indexes** to avoid maintenance costs

## Error Handling

### Common Exceptions

-   `DatastoreTimeoutException` - timeout during operation
-   `DatastoreFailureException` - operation failed
-   `TooManyResultsException` - query returned multiple results when expecting
    one
-   `IllegalArgumentException` - invalid cursor or transaction structure
-   `ConcurrentModificationException` - transaction conflict

### Transaction Errors

If you receive an exception during commit: - Transaction may have actually
succeeded - Design operations to be idempotent - Safe to retry in most cases

## Additional Features

### Statistics Overview

Access statistics about stored data: - Entity counts by kind - Property value
storage - Available through special query entities - Viewable in Google Cloud
Console

### API Packages

Primary package: `java import com.google.appengine.api.datastore.*;`

Key classes: - `DatastoreService` / `DatastoreServiceFactory` - `Entity` - `Key`
/ `KeyFactory` - `Query` / `PreparedQuery` - `Filter` / `FilterPredicate` /
`CompositeFilter`

## Data Consistency

### Consistency Levels

**Strongly Consistent:** - Guarantees freshest results - May take longer to
complete - Applies to: - Entity lookups by key - Ancestor queries (default) -
All operations within transactions

**Eventually Consistent:** - Generally runs faster - May occasionally return
stale results - May return entities that no longer match query criteria -
Applies to: - Non-ancestor queries (always) - Ancestor queries (if read policy
explicitly set)

### Setting Read Policy

```java
double deadline = 5.0;

// Construct read policy for eventual consistency
ReadPolicy policy = new ReadPolicy(ReadPolicy.Consistency.EVENTUAL);

// Set read policy
DatastoreServiceConfig eventuallyConsistentConfig =
    DatastoreServiceConfig.Builder.withReadPolicy(policy);

// Set call deadline
DatastoreServiceConfig deadlineConfig =
    DatastoreServiceConfig.Builder.withDeadline(deadline);

// Set both
DatastoreServiceConfig datastoreConfig =
    DatastoreServiceConfig.Builder
        .withReadPolicy(policy)
        .deadline(deadline);

// Get Datastore service with configuration
DatastoreService datastore =
    DatastoreServiceFactory.getDatastoreService(datastoreConfig);
```

**Default deadline**: 60 seconds (can be adjusted downward, not upward)

### Consistency Guidelines

1.  **Strong consistency when critical:**

    -   Use ancestor queries for entity group data
    -   Use key lookups for specific entities
    -   Perform operations within transactions

2.  **Eventual consistency acceptable when:**

    -   Slight staleness is tolerable (usually <few seconds)
    -   Query spans multiple entity groups
    -   Performance is priority over freshness

## Query Restrictions

### Entity Property Requirements

-   Entities must have values for ALL properties in filters and sorts
-   Entities lacking query properties are ignored
-   Cannot query for entities specifically lacking a property
-   Workaround: Use `null` as default, then filter for `null` values

### Unindexed Properties

-   Cannot filter or sort on unindexed properties
-   Queries return no results if filtering on unindexed properties
-   Types always unindexed: `Text`, `Blob`, `EmbeddedEntity`

### Inequality Filter Restrictions

**Single Property Rule:** - Maximum ONE property can have inequality filters -
Multiple inequality filters allowed on SAME property

```java
// VALID: Both inequalities on birthYear
Filter birthYearMinFilter =
    new FilterPredicate("birthYear", FilterOperator.GREATER_THAN_OR_EQUAL, minBirthYear);
Filter birthYearMaxFilter =
    new FilterPredicate("birthYear", FilterOperator.LESS_THAN_OR_EQUAL, maxBirthYear);
Filter birthYearRangeFilter =
    CompositeFilterOperator.and(birthYearMinFilter, birthYearMaxFilter);
Query q = new Query("Person").setFilter(birthYearRangeFilter);
```

```java
// INVALID: Inequalities on different properties
Filter birthYearMinFilter =
    new FilterPredicate("birthYear", FilterOperator.GREATER_THAN_OR_EQUAL, minBirthYear);
Filter heightMaxFilter =
    new FilterPredicate("height", FilterOperator.LESS_THAN_OR_EQUAL, maxHeight);
Filter invalidFilter = CompositeFilterOperator.and(birthYearMinFilter, heightMaxFilter);
Query q = new Query("Person").setFilter(invalidFilter);
```

**With Equality Filters:** Can combine equality filters on different properties
with inequality filters on one property:

```java
// VALID: Equality filters + inequality on single property
Filter lastNameFilter = new FilterPredicate("lastName", FilterOperator.EQUAL, targetLastName);
Filter cityFilter = new FilterPredicate("city", FilterOperator.EQUAL, targetCity);
Filter birthYearMinFilter =
    new FilterPredicate("birthYear", FilterOperator.GREATER_THAN_OR_EQUAL, minBirthYear);
Filter validFilter =
    CompositeFilterOperator.and(lastNameFilter, cityFilter, birthYearMinFilter);
Query q = new Query("Person").setFilter(validFilter);
```

### Sort Order Restrictions

**Inequality Property Must Be Sorted First:**

```java
// VALID: Sort on inequality property first
Filter birthYearMinFilter =
    new FilterPredicate("birthYear", FilterOperator.GREATER_THAN_OR_EQUAL, minBirthYear);
Query q = new Query("Person")
    .setFilter(birthYearMinFilter)
    .addSort("birthYear", SortDirection.ASCENDING)
    .addSort("lastName", SortDirection.ASCENDING);
```

```java
// INVALID: Missing sort on inequality property
Query q = new Query("Person")
    .setFilter(birthYearMinFilter)
    .addSort("lastName", SortDirection.ASCENDING);
```

```java
// INVALID: Sort on inequality property not first
Query q = new Query("Person")
    .setFilter(birthYearMinFilter)
    .addSort("lastName", SortDirection.ASCENDING)
    .addSort("birthYear", SortDirection.ASCENDING);
```

**Equality Filter Properties:** Sort orders ignored on properties with equality
filters (optimization).

**Undefined Default Order:** Without explicit sort order, result order may
change over time.

### Multi-Valued Property Behavior

**Surprising Interactions:**

Multiple inequality filters:

```java
// Entity with x = [1, 2] does NOT match
Query q =
    new Query("Widget")
        .setFilter(
            CompositeFilterOperator.and(
                new FilterPredicate("x", FilterOperator.GREATER_THAN, 1),
                new FilterPredicate("x", FilterOperator.LESS_THAN, 2)));
```

Neither value satisfies both filters.

Multiple equality filters:

```java
// Entity with x = [1, 2] DOES match
Query q =
    new Query("Widget")
        .setFilter(
            CompositeFilterOperator.and(
                new FilterPredicate("x", FilterOperator.EQUAL, 1),
                new FilterPredicate("x", FilterOperator.EQUAL, 2)));
```

At least one value satisfies each filter.

**Sort Order:**

-   Ascending: Uses smallest value
-   Descending: Uses greatest value - Entity with x = [1, 9] precedes x = [4, 5,
    6, 7] in BOTH directions

### Transaction Query Requirements

All queries in transactions MUST include ancestor filter.

## Projection Queries

Retrieve only specific properties instead of entire entities.

### Basic Usage

```java
private void addGuestbookProjections(Query query) {
    query.addProjection(new PropertyProjection("content", String.class));
    query.addProjection(new PropertyProjection("date", Date.class));
}

private void printGuestbookEntries(DatastoreService datastore, Query query, PrintWriter writer) {
    List<Entity> guests = datastore.prepare(query)
        .asList(FetchOptions.Builder.withLimit(100));
    for (Entity guest : guests) {
        String content = (String) guest.getProperty("content");
        Date stamp = (Date) guest.getProperty("date");
        writer.printf("Message %s posted on %s.\n", content, stamp.toString());
    }
}
```

**RawValue Alternative:** Pass `null` for type to get `RawValue` object: `java
query.addProjection(new PropertyProjection("content", null));`

### Grouping (Experimental)

```java
Query q = new Query("TestKind");
q.addProjection(new PropertyProjection("A", String.class));
q.addProjection(new PropertyProjection("B", Long.class));
q.setDistinct(true);
```

Returns only unique combinations of projected property values.

### Projection Limitations

1.  **Only indexed properties** can be projected
2.  **Cannot project same property** more than once
3.  **Cannot project properties** used in equality (EQUAL) or membership (IN)
    filters
4.  **Do not save projection results** back to Datastore (partially populated)

```java
// VALID: Projected property not in equality filter
SELECT A FROM kind WHERE B = 1

// VALID: Not an equality filter
SELECT A FROM kind WHERE A > 1

// INVALID: Projected property in equality filter
SELECT A FROM kind WHERE A = 1
```

### Multi-Valued Properties

Projection returns separate entity for each unique combination:

```java
// Entity: Foo(A=[1, 1, 2, 3], B=['x', 'y', 'x'])
SELECT A, B FROM Foo WHERE A < 3

// Returns 4 entities:
// A=1, B='x'
// A=1, B='y'
// A=2, B='x'
// A=2, B='y'
```

**Warning**: Multiple multi-valued properties in projection cause exploding
indexes.

### Index Requirements

All projected properties must be in a Datastore index. Development server
auto-generates these.

**Index Minimization:** Project same properties consistently to reduce index
count.

## Advanced Index Management

### Exploding Indexes

Occur when multiple multi-valued properties are indexed together.

**Example:** `java Query q = new Query("Widget")
.setFilter(CompositeFilterOperator.and( new FilterPredicate("x",
FilterOperator.EQUAL, 1), new FilterPredicate("y", FilterOperator.EQUAL, 2)))
.addSort("date", Query.SortDirection.ASCENDING);`

Suggested index:

```xml
<datastore-index kind="Widget" ancestor="false" source="manual">
  <property name="x" direction="asc"/>
  <property name="y" direction="asc"/>
  <property name="date" direction="asc"/>
</datastore-index>
```

Entity with multiple values:

```java
widget.setProperty("x", Arrays.asList(1, 2, 3, 4)); widget.setProperty("y",
Arrays.asList("red", "green", "blue")); widget.setProperty("date", new Date());
datastore.put(widget);
```

Requires |x| * |y| * |date| = 4 * 3 * 1 = **12 entries**

**Solution - Manual Index Configuration:**

```xml
<datastore-indexes autoGenerate="false">
  <datastore-index kind="Widget">
    <property name="x" direction="asc" />
    <property name="date" direction="asc" />
  </datastore-index>
  <datastore-index kind="Widget">
    <property name="y" direction="asc" />
    <property name="date" direction="asc" />
  </datastore-index>
</datastore-indexes>
```

Reduces to |x| * |date| + |y| * |date| = 4 + 3 = **7 entries**

### Index Limits

-   Maximum entries per entity
-   Maximum size per entity
-   Failure throws `IllegalArgumentException`

**Resolution for Error State Indexes:**

1.  Remove problematic index from `datastore-indexes.xml`
2.  Run: `gcloud datastore indexes cleanup datastore-indexes.xml`
3.  Fix the cause (reformulate query, remove problematic entities)
4.  Add corrected index back
5.  Run: `gcloud datastore indexes create datastore-indexes.xml`

**Avoiding Exploding Indexes:**

-   Avoid queries requiring custom indexes on list properties
-   Especially avoid: multiple sort orders or mixed equality/inequality filters

### Unindexed Properties

```java
DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
Key acmeKey = KeyFactory.createKey("Company", "Acme");

Entity tom = new Entity("Person", "Tom", acmeKey);
tom.setProperty("name", "Tom");
tom.setProperty("age", 32);
datastore.put(tom);

Entity lucy = new Entity("Person", "Lucy", acmeKey);
lucy.setProperty("name", "Lucy");
lucy.setUnindexedProperty("age", 29);  // Unindexed
datastore.put(lucy);

Filter ageFilter = new FilterPredicate("age", FilterOperator.GREATER_THAN, 25);
Query q = new Query("Person").setAncestor(acmeKey).setFilter(ageFilter);

// Returns tom but not lucy (age is unindexed)
List<Entity> results = datastore.prepare(q).asList(FetchOptions.Builder.withDefaults());
```

**Changing Index Status:** - Switch using `setProperty()` vs
`setUnindexedProperty()` - Existing entities not affected until rewritten - Must
get + put each entity to update indexes

## Async Datastore API

Execute non-blocking, parallel datastore operations.

### Basic Usage

```java
import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

AsyncDatastoreService datastore =
    DatastoreServiceFactory.getAsyncDatastoreService();

Key key = KeyFactory.createKey("Employee", "Max");

// Returns immediately
Future<Entity> entityFuture = datastore.get(key);

// Do other work while get executes in background...

// Blocks if not finished, otherwise returns instantly
Entity entity = entityFuture.get();
```

### Async Transactions

```java
void giveRaise(AsyncDatastoreService datastore, Key employeeKey, long raiseAmount)
    throws Exception {
    Future<Transaction> txn = datastore.beginTransaction();

    // Async lookup
    Future<Entity> employeeEntityFuture = datastore.get(employeeKey);

    // Create adjustment entity in parallel
    Entity adjustmentEntity = new Entity("SalaryAdjustment", employeeKey);
    adjustmentEntity.setProperty("adjustment", raiseAmount);
    adjustmentEntity.setProperty("adjustmentDate", new Date());
    datastore.put(adjustmentEntity);

    // Wait for lookup to complete
    Entity employeeEntity = employeeEntityFuture.get();
    long salary = (Long) employeeEntity.getProperty("salary");
    employeeEntity.setProperty("salary", salary + raiseAmount);

    datastore.put(employeeEntity);
    txn.get().commit();  // Blocks on all outstanding async calls
}
```

**Transaction Commit Behavior:** - `commit()` blocks until all async calls
complete - `commitAsync()` returns `Future` that blocks on `get()` -
Transactions associated with thread, not service instance

### Async Queries

Queries are implicitly async:

```java
.setFilter(new FilterPredicate("dateOfHire", FilterOperator.LESS_THAN,
oneMonthAgo)); // Returns instantly, query executes in background
Iterable<Entity> recentHires = datastore.prepare(q1).asIterable();

Query q2 = new Query("Customer") .setFilter(new FilterPredicate("lastContact",
FilterOperator.GREATER_THAN, oneYearAgo)); // Also returns instantly
Iterable<Entity> needsFollowup = datastore.prepare(q2).asIterable();

schedulePhoneCall(recentHires, needsFollowUp);
```

### When to Use Async

**Good candidates:**

- Multiple independent datastore operations
- Operations
without data dependencies
- Operations that can execute in parallel

**Performance benefit:**
- Similar CPU usage
- Lower latency (parallel execution)

**Example comparison:**

Synchronous (sequential):

```java DatastoreService datastore =
DatastoreServiceFactory.getDatastoreService(); Key empKey =
KeyFactory.createKey("Employee", "Max");

Entity employee = datastore.get(empKey); // Blocks unnecessarily

Query query = new Query("PaymentHistory"); PreparedQuery pq =
datastore.prepare(query); List<Entity> result =
pq.asList(FetchOptions.Builder.withLimit(10));

renderHtml(employee, result);
```

Asynchronous (parallel):

```java
DatastoreServiceFactory.getAsyncDatastoreService(); Key empKey =
KeyFactory.createKey("Employee", "Max");

Future<Entity> employeeFuture = datastore.get(empKey); // Returns immediately

Query query = new Query("PaymentHistory", empKey); PreparedQuery pq =
datastore.prepare(query); List<Entity> result =
pq.asList(FetchOptions.Builder.withLimit(10));

Entity employee = employeeFuture.get(); // May block renderHtml(employee,
result);
```

### Future Considerations

-   `Future.get(timeout, unit)` timeout separate from RPC deadline
-   `Future.cancel()` doesn't guarantee unchanged datastore state
-   Exceptions not thrown until `get()` called

## Datastore Callbacks

Execute code at various points in persistence process.

### Callback Types

**PrePut** - Before entity put:

```java
import com.google.appengine.api.datastore.PrePut;
import com.google.appengine.api.datastore.PutContext;

class PrePutCallbacks {
  @PrePut(kinds = {"Customer", "Order"})
  void log(PutContext context) {
    logger.fine("Putting " + context.getCurrentElement().getKey());
  }

  @PrePut // Applies to all kinds
  void updateTimestamp(PutContext context) {
    context.getCurrentElement().setProperty("last_updated", new Date());
  }
}
```

**PostPut** - After entity put:

```java
@PostPut(kinds = {"Customer", "Order"})
void log(PutContext context) {
  logger.fine("Finished putting " + context.getCurrentElement().getKey());
}
```

**PreDelete** - Before entity delete:

```java
@PreDelete(kinds = {"Customer", "Order"})
void checkAccess(DeleteContext context) {
  if (!Auth.canDelete(context.getCurrentElement())) {
    throw new SecurityException();
  }
}
```

**PostDelete** - After entity delete:

```java
@PostDelete(kinds = {"Customer", "Order"})
void log(DeleteContext context) {
  logger.fine("Finished deleting " + context.getCurrentElement().getKey());
}
```

**PreGet** - Before entity retrieval:

```java
@PreGet(kinds = {"Customer", "Order"})
public void preGet(PreGetContext context) {
  Entity e = MyCache.get(context.getCurrentElement());
  if (e != null) {
    context.setResultForCurrentElement(e);
  }
}
```

**PreQuery** - Before query execution:

```java
@PreQuery(kinds = {"Customer"})
public void preQuery(PreQueryContext context) {
  UserService users = UserServiceFactory.getUserService();
  context
      .getCurrentElement()
      .setFilter(
          new FilterPredicate("owner", Query.FilterOperator.EQUAL, users.getCurrentUser()));
}
```

**PostLoad** - After entity load:

```java
@PostLoad(kinds = {"Order"})
public void postLoad(PostLoadContext context) {
  context.getCurrentElement().setProperty("read_timestamp", System.currentTimeMillis());
}
```

### Callback Requirements

All callback methods must: - Be instance methods - Return `void` - Accept single
context parameter - Not throw checked exceptions - Belong to class with no-arg
constructor

### Batch Operations

Callbacks invoked once per entity:

```java
@PrePut(kinds = "TicketOrder")
void checkBatchSize(PutContext context) {
  if (context.getElements().size() > 5) {
    throw new IllegalArgumentException("Cannot purchase more than 5 tickets at once.");
  }
}
```

Execute once per batch:

```java
@PrePut
void log(PutContext context) {
  if (context.getCurrentIndex() == 0) {
    logger.fine("Putting batch of size " + context.getElements().size());
  }
}
```

### Async Interaction

-   `Pre*` callbacks execute synchronously
-   `Post*` callbacks execute synchronously but not until `Future.get()` called
-   Must call `get()` to trigger `Post*` callbacks

### Common Errors to Avoid

1.  **Do not maintain non-static state** - callback instance lifecycle is
    unpredictable
2.  **Do not assume callback execution order** - only guaranteed Pre* before
    Post*
3.  **One callback per method** - cannot use multiple annotations
4.  **Do not forget to retrieve async results** - Post* won't run without
    `get()`
5.  **Avoid infinite loops** - restrict callbacks to specific kinds

**Not Triggered**: Callbacks do not fire for Remote API calls.

## Metadata Queries

Access Datastore metadata programmatically.

### Special Entity Kinds

-   `__namespace__` - Namespaces
-   `__kind__` - Entity kinds
-   `__property__` - Properties
-   `__Stat_*` - Statistics entities

### Entity Group Metadata

Get entity group version:

```java
private static long getEntityGroupVersion(
    DatastoreService ds, Transaction tx, Key entityGroupKey) {
  try {
    return Entities.getVersionProperty(ds.get(tx, Entities.createEntityGroupKey(entityGroupKey)));
  } catch (EntityNotFoundException e) {
    return 0;
  }
}
```

Entity group version increases on every change (strictly positive number).

### Namespace Queries

```java
void printAllNamespaces(DatastoreService ds, PrintWriter writer) {
    Query q = new Query(Entities.NAMESPACE_METADATA_KIND);
    for (Entity e : ds.prepare(q).asIterable()) {
        if (e.getKey().getId() != 0) {
            writer.println("<default>");
        } else {
            writer.println(e.getKey().getName());
        }
    }
}
```

Default namespace has numeric ID 1 (empty string not valid key name).

### Kind Queries

```java
void printLowercaseKinds(DatastoreService ds, PrintWriter writer) {
    Query q = new Query(Entities.KIND_METADATA_KIND);
    List<Filter> subFils = new ArrayList();

    subFils.add(new FilterPredicate(Entity.KEY_RESERVED_PROPERTY,
        FilterOperator.GREATER_THAN_OR_EQUAL, Entities.createKindKey("a")));

    String endChar = Character.toString((char) ('z' + 1));
    subFils.add(new FilterPredicate(Entity.KEY_RESERVED_PROPERTY,
        FilterOperator.LESS_THAN, Entities.createKindKey(endChar)));

    q.setFilter(CompositeFilterOperator.and(subFils));

    for (Entity e : ds.prepare(q).asIterable()) {
        writer.println("  " + e.getKey().getName());
    }
}
```

### Property Queries

**Keys-only** (indexed properties only):

```java
void printProperties(DatastoreService ds, PrintWriter writer) {
  Query q = new Query(Entities.PROPERTY_METADATA_KIND).setKeysOnly();
  for (Entity e : ds.prepare(q).asIterable()) {
    writer.println(e.getKey().getParent().getName() + ": " + e.getKey().getName());
  }
}
```

**Non-keys-only** (property representations):

```java
Collection<String> representationsOfProperty(DatastoreService ds, String kind, String property) {
  Query q = new Query(Entities.PROPERTY_METADATA_KIND);
  q.setFilter(
      new FilterPredicate(
          "__key__", Query.FilterOperator.EQUAL, Entities.createPropertyKey(kind, property)));

// ...
Entity propInfo = ds.prepare(q).asSingleEntity();
return (Collection<String>) propInfo.getProperty("property_representation");


}
```

### Metadata Performance

-   Current data (not cached like dashboard)
-   Slow execution (N entities ≈ N separate queries)
-   Non-keys-only property queries slower than keys-only
-   Entity group metadata gets faster than regular entities
-   Billed same as regular datastore operations

## Datastore Statistics

Access via special entity kinds starting/ending with `__`.

### Available Statistics

| Statistic          | Entity Kind                   | Description             |
| ------------------ | ----------------------------- | ----------------------- |
| All entities       | `__Stat_Total__`              | Complete entity         |
:                    :                               : statistics              :
| Entities by        | `__Stat_Namespace__`          | Per-namespace           |
: namespace          :                               : statistics              :
| Entities by kind   | `__Stat_Kind__`               | Per-kind statistics     |
| Root entities      | `__Stat_Kind_IsRootEntity__`  | Root entities per kind  |
| Non-root entities  | `__Stat_Kind_NotRootEntity__` | Child entities per kind |
| Properties by type | `__Stat_PropertyType__`       | Properties by value     |
:                    :                               : type                    :
| Properties by      | `__Stat_PropertyType_Kind__`  | Per kind and type       |
: kind/type          :                               :                         :
| Properties by      | `__Stat_PropertyName_Kind__`  | Per name and kind       |
: name/kind          :                               :                         :

Namespace-specific versions: Prefix with `__Stat_Ns_`

### Common Properties

All statistic entities have: - `count` - Number of items (long) - `bytes` -
Total size in bytes (long) - `timestamp` - Last update time (date-time)

Additional properties vary by statistic type (e.g., `kind_name`,
`property_type`, `entity_bytes`, `builtin_index_bytes`, `composite_index_bytes`,
etc.)

### Usage Example

```java
DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
Entity globalStat = datastore.prepare(new Query("__Stat_Total__")).asSingleEntity();
Long totalBytes = (Long) globalStat.getProperty("bytes");
Long totalEntities = (Long) globalStat.getProperty("count");
```

### Best Practice

Query for `__Stat_Total__` with most recent timestamp, then use that timestamp
to filter other statistics for consistency.

### Statistics Pruning

For apps with thousands of namespaces/kinds/properties, Datastore progressively
drops statistics to manage overhead: 1. Per-namespace, per-kind, per-property
stats 2. Per-kind and per-property stats 3. Per-namespace and per-kind stats 4.
Per-kind stats 5. Per-namespace stats 6. Summary statistics (never dropped)

## Structuring for Strong Consistency

### The Trade-off

-   **Strong consistency**: Current data, ancestor queries required, ~1
    write/second per entity group
-   **Eventual consistency**: Higher throughput, may show stale data (usually
    <few seconds)

### Design Patterns

**Pattern 1: Maximum Throughput (Eventually Consistent)**

```java
protected Entity createGreeting(DatastoreService datastore, User user, Date date, String content) {
  // No parent - each greeting is root entity
  Entity greeting = new Entity("Greeting");
  greeting.setProperty("user", user);
  greeting.setProperty("date", date);
  greeting.setProperty("content", content);
  datastore.put(greeting);
  return greeting;
}

protected List<Entity> listGreetingEntities(DatastoreService datastore) {
  // Non-ancestor query - eventually consistent
  Query query = new Query("Greeting").addSort("date", Query.SortDirection.DESCENDING);
  return datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10));
}
```

**Pattern 2: Strong Consistency (Lower Throughput)**

```java
protected Entity createGreeting(DatastoreService datastore, User user, Date date, String content) {
  Key guestbookKey = KeyFactory.createKey("Guestbook", guestbookName);

  // Same entity group for all greetings
  Entity greeting = new Entity("Greeting", guestbookKey);
  greeting.setProperty("user", user);
  greeting.setProperty("date", date);
  greeting.setProperty("content", content);
  datastore.put(greeting);
  return greeting;
}

protected List<Entity> listGreetingEntities(DatastoreService datastore) {
  Key guestbookKey = KeyFactory.createKey("Guestbook", guestbookName);

  // Ancestor query - strongly consistent
  Query query =
      new Query("Greeting", guestbookKey)
          .setAncestor(guestbookKey)
          .addSort("date", Query.SortDirection.DESCENDING);
  return datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10));
}
```

### When Heavy Write Usage Expected

For applications exceeding 1 write/second per entity group: - Use memcache for
recent posts with expiration - Cache in cookies - Put state in URL - Mix recent
(cached) and older (datastore) posts - Goal: Provide current user's data during
active session

**Remember**: Gets, ancestor queries, and transactional operations always see
latest data.
