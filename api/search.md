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

# Google App Engine Search API Documentation

*   [Overview](#overview)
*   [Core Concepts](#core-concepts)
    *   [Documents](#documents)
    *   [Field Types](#field-types)
    *   [Indexes](#indexes)
    *   [Queries](#queries)
    *   [Results](#results)
*   [Document Operations](#document-operations)
    *   [Creating Documents](#creating-documents)
    *   [Adding Documents to Index](#adding-documents-to-index)
    *   [Updating Documents](#updating-documents)
    *   [Retrieving Documents](#retrieving-documents)
    *   [Deleting Documents](#deleting-documents)
*   [Query Strings](#query-strings)
    *   [Global Search](#global-search)
    *   [Field Search](#field-search)
    *   [Relational Operators by Field Type](#relational-operators-by-field-type)
    *   [Geopoint Queries](#geopoint-queries)
    *   [Special Features](#special-features)
*   [Tokenization](#tokenization)
*   [Query and Sort Options](#query-and-sort-options)
    *   [Basic Query Construction](#basic-query-construction)
    *   [QueryOptions Properties](#queryoptions-properties)
    *   [Sort Options](#sort-options)
    *   [Field Expressions](#field-expressions)
    *   [Snippets](#snippets)
*   [Handling Results](#handling-results)
    *   [Processing Results](#processing-results)
    *   [Using Offsets](#using-offsets)
    *   [Using Cursors (Recommended for Large Result Sets)](#using-cursors-recommended-for-large-result-sets)
    *   [Saving/Restoring Cursors](#savingrestoring-cursors)
*   [Faceted Search](#faceted-search)
    *   [Adding Facets to Documents](#adding-facets-to-documents)
    *   [Retrieving Facet Information](#retrieving-facet-information)
    *   [Facet Options](#facet-options)
    *   [Using Refinements](#using-refinements)
*   [Index Management](#index-management)
    *   [Index Schemas](#index-schemas)
    *   [Retrieving All Indexes](#retrieving-all-indexes)
    *   [Checking Index Size](#checking-index-size)
    *   [Deleting an Index](#deleting-an-index)
*   [Best Practices](#best-practices)
    *   [Performance Optimization](#performance-optimization)
    *   [Data Modeling](#data-modeling)
*   [Quotas and Limits](#quotas-and-limits)
    *   [Free Quotas](#free-quotas)
    *   [Safety Limits (All Apps)](#safety-limits-all-apps)
    *   [Pricing (Beyond Free Quotas)](#pricing-beyond-free-quotas)
*   [Important Characteristics](#important-characteristics)
    *   [Eventual Consistency](#eventual-consistency)
    *   [Document Properties](#document-properties)
    *   [Date Field Precision](#date-field-precision)
*   [Local Development Server Limitations](#local-development-server-limitations)
*   [Common Patterns](#common-patterns)
    *   [Search with Pagination](#search-with-pagination)
    *   [Error Handling with Retry](#error-handling-with-retry)
*   [Quick Reference](#quick-reference)
    *   [Query String Syntax](#query-string-syntax)
    *   [Field Type Selection Guide](#field-type-selection-guide)

## Overview

The Search API provides a model for indexing documents with structured data,
enabling full-text search operations with advanced querying capabilities.
Documents and indexes are stored in a separate persistent store optimized for
search operations.

## Core Concepts

### Documents

A document is an object with:

-   **Unique ID** (doc_id): Up to 500 printable ASCII characters (codes 33-126)
-   **List of fields**: Named, typed data containers
-   **Maximum size**: 1 MB per document

#### Document Identifier Rules

-   Can be auto-generated or manually specified
-   Cannot begin with `!` or be wrapped with `__`
-   Must contain only visible, printable ASCII characters
-   Used for direct retrieval without search

### Field Types

#### String Fields

-   **Text Field**: Plain text, searchable word-by-word (max 1,048,576
    characters)
-   **HTML Field**: HTML markup, only text outside tags is searchable (max
    1,048,576 characters)
-   **Atom Field**: Indivisible string, not tokenized (max 500 characters)

#### Non-Text Fields

-   **Number Field**: A double-precision floating-point number. Values for this
    field must be in the range -2,147,483,647 to 2,147,483,647.
-   **Date Field**: Date object (stored as days since 1/1/1970 UTC)
-   **Geopoint Field**: Latitude and longitude coordinates

#### Field Naming Rules

-   Case sensitive, ASCII only
-   Must start with letter
-   Can contain letters, digits, underscore
-   Maximum 500 characters

### Indexes

An index stores documents for retrieval. Key characteristics:

-   No limit on number of documents or indexes

-   Default size limit: 10 GB per index (can increase to 200 GB)

-   Total storage across all indexes: 0.25 GB free quota

-   Supports document retrieval by ID, ID range, or query

### Queries

Query strings can be:

-   **Global search**: Search values across all fields
-   **Field search**: Target specific fields by name
-   **Mixed**: Combine both approaches - Maximum length: 2000 characters

### Results

Search results include:

-   Number of documents found (estimate)
-   Number of documents returned (actual)
-   Collection of ScoredDocument objects - Maximum 10,000 matching documents per
    search
-   Default return: 20 documents at a time

## Document Operations

### Creating Documents

```java
User currentUser = UserServiceFactory.getUserService().getCurrentUser();
String userEmail = currentUser == null ? "" : currentUser.getEmail();
String myDocId = "PA6-5000";

Document doc = Document.newBuilder()
    .setId(myDocId)  // Optional
    .addField(Field.newBuilder().setName("content").setText("the rain in spain"))
    .addField(Field.newBuilder().setName("email").setText(userEmail))
    .addField(Field.newBuilder().setName("published").setDate(new Date()))
    .build();
```

### Adding Documents to Index

```java
IndexSpec indexSpec = IndexSpec.newBuilder().setName(indexName).build();
Index index = SearchServiceFactory.getSearchService().getIndex(indexSpec);

// Batch put (up to 200 documents)
index.put(document);
```

**Best Practice**: Batch operations with up to 200 documents are more efficient
than single additions.

### Updating Documents

Documents are immutable once added. To update: 1. Create a new document with the
same doc_id 2. Add it to the index (replaces the old document)

### Retrieving Documents

```java
// By doc_id
Document doc = index.get("AZ125");

// Range of doc_ids
GetResponse<Document> docs = index.getRange(
    GetRequest.newBuilder()
        .setStartId("AZ125")
        .setLimit(100)
        .build()
);
```

### Deleting Documents

```java
// Delete by doc_id (batch up to 200)
List<String> docIds = new ArrayList<>();
docIds.add("doc1");
docIds.add("doc2");
index.delete(docIds);
```

## Query Strings

### Global Search

Search for values in any field:

```java
"rose water"              // Find documents with both words
"1776-07-04"             // Find date or text matching this
"NOT red"                // Find documents without "red"
"red OR blue"            // Find either color
"keyboard AND mouse"     // Find both terms
```

**Boolean Operator Precedence**: NOT > OR > AND

### Field Search

Target specific fields:

```java
"product:piano"                          // Equality
"price < 500"                            // Comparison
"product:piano AND price < 5000"         // Combined
"color:(red OR blue)"                    // Multiple values
"birthday >= 2000-12-31"                 // Date comparison
```

### Relational Operators by Field Type

Field Type | Operators
---------- | -------------------------
Atom       | `:` `=`
Text/HTML  | `:` `=`
Number     | `:` `=` `<` `<=` `>` `>=`
Date       | `:` `=` `<` `<=` `>` `>=`
Geopoint   | Use `distance()` function

### Geopoint Queries

```java
"distance(survey_marker, geopoint(35.2, 40.5)) < 100"
"distance(home, geopoint(35.2, 40.5)) > 100"
```

### Special Features

#### Stemming

Use `~` prefix to match word variations: 
```
"~cat" // Matches "cat" and "cats"
"~dog" // Matches "dog" and "dogs"
```

#### Quoted Strings

Exact phrase matching: 

```
 "Comment:\"insanely great\"" "Title:\"Tom&Jerry\""
```

## Tokenization

String fields are tokenized on: - Whitespace characters - Most punctuation
marks - Special characters

**Special Cases**: - Underscore `_` and ampersand `&` do NOT break tokens -
Acronyms like "I.B.M." become "ibm" - Hash signs in patterns like `#google` or
`c#` remain part of word - Apostrophe in possessives like "John's" stays
attached

**Atom Fields**: Never tokenized (exact match only)

## Query and Sort Options

### Basic Query Construction

```java
QueryOptions options = QueryOptions.newBuilder()
    .setLimit(25)
    .setFieldsToReturn("model", "price", "description")
    .build();

Query query = Query.newBuilder()
    .setOptions(options)
    .build(queryString);

Results<ScoredDocument> result = index.search(query);
```

### QueryOptions Properties

Property            | Description                | Default    | Maximum
------------------- | -------------------------- | ---------- | ----------
Limit               | Max documents to return    | 20         | 1000
Offset              | Starting position          | 0          | 1000
Cursor              | Alternative to offset      | null       | -
ReturningIdsOnly    | Return IDs only            | false      | -
FieldsToReturn      | Specific fields to include | All fields | 100 fields
ExpressionsToReturn | Computed fields            | None       | -
FieldsToSnippet     | Generate snippets          | None       | -

### Sort Options

```java
SortOptions sortOptions = SortOptions.newBuilder()
    .addSortExpression(
        SortExpression.newBuilder()
            .setExpression("price")
            .setDirection(SortExpression.SortDirection.DESCENDING)
            .setDefaultValueNumeric(0)
    )
    .addSortExpression(
        SortExpression.newBuilder()
            .setExpression("brand")
            .setDirection(SortExpression.SortDirection.DESCENDING)
            .setDefaultValue("")
    )
    .setLimit(1000)
    .build();
```

**Important**: Sorting limits results to 10,000 documents maximum. Default sort
limit is 1,000.

### Field Expressions

Create computed fields using expressions:

```java
"price * quantity"
"(men + women)/2"
"min(daily_use, 10) * rate"
"snippet('rose', flower, 120)"
```

**Special Terms**: - `_rank`: Document's rank property - `_score`: Match score
(if MatchScorer enabled)

**Numeric Functions**: - `max(...)`, `min(...)`, `abs(...)`, `log(...)`, `pow(x,
y)`, `count(field)`

**Geopoint Functions**: - `geopoint(lat, long)`: Create geopoint -
`distance(point1, point2)`: Calculate distance in meters

### Snippets

Generate text fragments showing matched content:

```java
snippet(query, body, [max_chars])

// Example
QueryOptions options = QueryOptions.newBuilder()
    .setFieldsToSnippet("description", "content")
    .build();
```

Returns HTML with matched text in boldface, default 160 characters.

## Handling Results

### Processing Results

```java
Results<ScoredDocument> result = index.search(query);

// Get counts
long totalMatches = result.getNumberFound();
int numberOfDocsReturned = result.getNumberReturned();

// Iterate documents
for (ScoredDocument doc : result) {
    String maker = doc.getOnlyField("maker").getText();
    double price = doc.getOnlyField("price").getNumber();
}
```

### Using Offsets

```java
int offset = 0;
do {
    QueryOptions options = QueryOptions.newBuilder()
        .setOffset(offset)
        .build();

    Query query = Query.newBuilder()
        .setOptions(options)
        .build(queryString);

    Results<ScoredDocument> result = index.search(query);
    int numberRetrieved = result.getNumberReturned();

    if (numberRetrieved > 0) {
        offset += numberRetrieved;
        // Process documents
    }
} while (numberRetrieved > 0);
```

### Using Cursors (Recommended for Large Result Sets)

#### Per-Query Cursor

```java
Cursor cursor = Cursor.newBuilder().build();

do {
    QueryOptions options = QueryOptions.newBuilder()
        .setCursor(cursor)
        .build();

    Query query = Query.newBuilder()
        .setOptions(options)
        .build(queryString);

    Results<ScoredDocument> result = index.search(query);
    cursor = result.getCursor();

    // Process documents
} while (cursor != null);
```

#### Per-Result Cursor

```java
Cursor cursor = Cursor.newBuilder()
    .setPerResult(true)
    .build();

QueryOptions options = QueryOptions.newBuilder()
    .setCursor(cursor)
    .build();

Results<ScoredDocument> result = index.search(query);

for (ScoredDocument doc : result) {
    if (/* document of interest */) {
        cursor = doc.getCursor();
    }
}
```

#### Saving/Restoring Cursors

```java
// Save
String cursorString = cursor.toWebSafeString();

// Restore
Cursor cursor = Cursor.newBuilder().build(cursorString);
```

## Faceted Search

### Adding Facets to Documents

```java
Document doc = Document.newBuilder()
    .setId("doc1")
    .addField(Field.newBuilder().setName("name").setAtom("x86"))
    .addFacet(Facet.withAtom("type", "computer"))
    .addFacet(Facet.withNumber("ram_size_gb", 8.0))
    .build();
```

**Facet Rules**: - Name: Same rules as field names (500 char max) - Value: Atom
string (500 char max) or number - No limit on values per facet or facets per
document - Can have multiple values for same facet name

### Retrieving Facet Information

#### Automatic Discovery

```java
Results<ScoredDocument> result = index.search(
    Query.newBuilder()
        .setEnableFacetDiscovery(true)
        .build("name:x86")
);

for (FacetResult facetResult : result.getFacets()) {
    System.out.printf("Facet %s:\n", facetResult.getName());
    for (FacetResultValue facetValue : facetResult.getValues()) {
        System.out.printf(" %s: Count=%s\n",
            facetValue.getLabel(),
            facetValue.getCount()
        );
    }
}
```

#### By Name

```java
Results<ScoredDocument> result = index.search(
    Query.newBuilder()
        .addReturnFacet("type")
        .addReturnFacet("ram_size_gb")
        .build("name:x86")
);
```

#### By Name and Value

```java
Results<ScoredDocument> result = index.search(
    Query.newBuilder()
        .addReturnFacet(FacetRequest.newBuilder()
            .setName("type")
            .addValueConstraint("computer")
            .addValueConstraint("printer"))
        .addReturnFacet(FacetRequest.newBuilder()
            .setName("ram_size_gb")
            .addRange(FacetRange.withEnd(4.0))
            .addRange(FacetRange.withStartEnd(4.0, 8.0))
            .addRange(FacetRange.withStart(8.0)))
        .build("name:x86")
);
```

### Facet Options

```java
Query query = Query.newBuilder()
    .setFacetOptions(FacetOptions.newBuilder()
        .setDiscoveryLimit(5)         // Default: 10
        .setDiscoveryValueLimit(10)   // Default: 10
        .setDepth(6000)               // Default: 1000
        .build())
    .build(queryString);
```

### Using Refinements

```java
Query query = Query.newBuilder()
    .addFacetRefinementFromToken(refinement_key1)
    .addFacetRefinementFromToken(refinement_key2)
    .build("some_query");
```

**Refinement Logic**: - Same facet refinements: Combined with OR - Different
facet refinements: Combined with AND

## Index Management

### Index Schemas

Schemas are maintained automatically and show all field names and types:

```java
GetResponse<Index> response = SearchServiceFactory.getSearchService()
    .getIndexes(GetIndexesRequest.newBuilder()
        .setSchemaFetched(true)
        .build());

for (Index index : response) {
    Schema schema = index.getSchema();
    for (String fieldName : schema.getFieldNames()) {
        List<FieldType> typesForField = schema.getFieldTypes(fieldName);
        // Process schema information
    }
}
```

**Schema Characteristics**: - Auto-updated as documents are added - Fields can
never be removed from schema - Same field name can have multiple types - Not
returned by default (must request explicitly)

### Retrieving All Indexes

```java
// Current namespace only
GetResponse<Index> response = SearchServiceFactory.getSearchService()
    .getIndexes(GetIndexesRequest.newBuilder().build());

// All namespaces
GetResponse<Index> response = SearchServiceFactory.getSearchService()
    .getIndexes(GetIndexesRequest.newBuilder()
        .setAllNamespaces(true)
        .build());
```

**Pagination**: Maximum 1000 indexes per call. Use `setStartIndexName()` for
more.

### Checking Index Size

```java
// Maximum allowed size
long maxSize = index.getStorageLimit();

// Current usage (estimate)
long currentUsage = index.getStorageUsage();
```

### Deleting an Index

To delete an index: 1. Delete all documents 2. Delete the index schema

```java
// Delete all documents
while (true) {
    List<String> docIds = new ArrayList<>();
    GetRequest request = GetRequest.newBuilder()
        .setReturningIdsOnly(true)
        .build();
    GetResponse<Document> response = index.getRange(request);

    if (response.getResults().isEmpty()) {
        break;
    }

    for (Document doc : response) {
        docIds.add(doc.getId());
    }
    index.delete(docIds);
}
```

## Best Practices

### Performance Optimization

1.  **Batch Operations**: Always batch puts/deletes (up to 200 documents)

2.  **Use Document Rank for Pre-sorting**: `java // Set rank to price for
    default price sorting Document.newBuilder().setRank(price)`

3.  **Avoid Expensive Operations**:

    -   Use atom fields for boolean data (not numbers)
    -   Transform negations: `cuisine_known:yes` vs `NOT cuisine:undefined`
    -   Transform disjunctions: `cuisine:Asian` vs `cuisine:Japanese OR
        cuisine:Korean`
    -   Eliminate tautologies: `city:toronto` vs `city:toronto AND NOT
        city:montreal`

4.  **Narrow Before Sorting**: 

```java // Bad: Sort 1M documents
"cuisine:japanese" + sort by distance

// Good: Filter first, then sort smaller set 
"cuisine:japanese AND city:<user-city>" + sort by distance
```

5.  **Use Categories to Avoid Sorting**:
```java
// Create price ranges:
    price_0_10, price_11_20, etc. "price_range:price_21_30 OR
    price_range:price_31_40"
```

6.  **Avoid Scoring Unless Needed**: Scoring is expensive in operations and time

### Data Modeling

1.  **Use Rank Strategically**: Default sort by rank is most efficient

2.  **Multiple Sort Orders**: Create separate indexes for different sort orders

    -   Index 1: `rank = price`
    -   Index 2: `rank = MAXINT - price`

3.  **Multi-valued Fields**: Only first value used in sorts

4.  **Document ID Design**: Can't search on doc_id directly, so also store in
    atom field if needed

## Quotas and Limits

### Free Quotas

Resource         | Free Quota
---------------- | -----------
Total storage    | 0.25 GB
Queries          | 1000/day
Adding documents | 0.01 GB/day

### Safety Limits (All Apps)

Resource                 | Limit
------------------------ | ---------------------------------
Query execution time     | 100 aggregated minutes/minute
Documents added/deleted  | 15,000/minute
Index size               | 10 GB (up to 200 GB with request)
Document size            | 1 MB
Query string length      | 2000 characters
Documents per search     | 10,000 max found
Documents per put/delete | 200
Fields to return         | 100
Sort limit               | 10,000 (default 1,000)

### Pricing (Beyond Free Quotas)

Resource | Cost
-------- | -----------------
Storage  | $0.18/GB/month
Queries  | $0.50/10K queries
Indexing | $2.00/GB

## Important Characteristics

### Eventual Consistency

Changes to documents propagate across data centers with eventual consistency: -
Updates may not be immediately visible - Search results may not reflect most
recent changes - Designed for high availability across distributed systems

### Document Properties

```java
Document doc = Document.newBuilder()
    .setId(docId)
    .setRank(customRank)    // Default: seconds since Jan 1, 2011
    .setLocale("en")        // Language encoding
    .addField(...)
    .build();
```

**Rank Usage**: - Positive integer determining default sort order - Don't assign
same rank to >10,000 documents - Referenced as `_rank` in expressions

### Date Field Precision

-   Stored as days since 1/1/1970 UTC
-   Time component ignored for indexing/searching
-   Query format: `yyyy-mm-dd` (leading zeros optional)
-   Sort order for same date is undefined

## Local Development Server Limitations

Features NOT available on local dev server: - Stemming (e.g., `~cat`) - Asian
language tokenization - Match scoring - Diacritical marks in atom/text/HTML
fields

## Common Patterns

### Search with Pagination

```java
public Results<ScoredDocument> searchWithOptions(
    String indexName,
    String queryString
) {
    SortOptions sortOptions = SortOptions.newBuilder()
        .addSortExpression(
            SortExpression.newBuilder()
                .setExpression("price")
                .setDirection(SortExpression.SortDirection.DESCENDING)
                .setDefaultValueNumeric(0))
        .setLimit(1000)
        .build();

    QueryOptions options = QueryOptions.newBuilder()
        .setLimit(25)
        .setFieldsToReturn("model", "price", "description")
        .setSortOptions(sortOptions)
        .build();

    Query query = Query.newBuilder()
        .setOptions(options)
        .build(queryString);

    IndexSpec indexSpec = IndexSpec.newBuilder()
        .setName(indexName)
        .build();
    Index index = SearchServiceFactory.getSearchService()
        .getIndex(indexSpec);

    return index.search(query);
}
```

### Error Handling with Retry

```java
final int maxRetry = 3;
int attempts = 0;
int delay = 2;

while (true) {
    try {
        index.put(document);
        break;
    } catch (PutException e) {
        if (StatusCode.TRANSIENT_ERROR.equals(e.getOperationResult().getCode())
            && ++attempts < maxRetry) {
            Thread.sleep(delay * 1000);
            delay *= 2;  // Exponential backoff
            continue;
        } else {
            throw e;
        }
    }
}
```

## Quick Reference

### Query String Syntax

```
Global:          "value1 value2"
Field:           "field:value"
Comparison:      "price < 100"
Boolean:         "field1:value1 AND field2:value2"
Negation:        "NOT field:value"
Parentheses:     "(field1:value1 OR field2:value2) AND field3:value3"
Stemming:        "~word"
Exact phrase:    "field:\"exact phrase\""
Geopoint:        "distance(field, geopoint(lat, long)) < 100"
```

### Field Type Selection Guide

-   **Atom**: Exact match only (product IDs, categories, booleans)
-   **Text**: Word-by-word search (descriptions, comments)
-   **HTML**: Like text but ignores markup (formatted content)
-   **Number**: Numeric comparisons (prices, quantities)
-   **Date**: Date range queries (timestamps, birthdays)
-   **Geopoint**: Distance calculations (locations, coordinates)
