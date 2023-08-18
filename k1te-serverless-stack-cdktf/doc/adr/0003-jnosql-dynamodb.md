# 1. Use Jnosql dynamodb provider to work with dynamodb

Date: 2023-08-17

## Status

Under consideration

## Context

[Eclipse JNosql](https://github.com/eclipse/jnosql-databases) recently reached 1.0.1 release

JNosql is seems a RI of the new JakartaEE API similar in idea to Spring Data but for NoSQL databases

Quarkus seems also has JNosql extension https://github.com/quarkiverse/quarkus-jnosql and dynamodb connector.

## Facing

We currently use lighweight ORM-like extension over AWS DynamoDB client named **Enhanced DynamoDB client**

The question is whether JNosql is able to support advanced DynamoDB features we use like conditionals, secondary indices, transactions, etc.

This requires at least studying documentation and protityping.

## Advantages

Using open JNosql API instead of proprietary one we may be able to reuse our knowledge with other nosql databases in the future.

JNosql may have more concise and convenient API to use than enhanced dynamodb client

## Disadvantages

JNosql implementation may have larger size and contribute to a larger lambda initialization time and processing time.

Being least common denominator across different nosql databases, it may not support all of the features DynamoDB provides.

Rewriting working solution may introduce new bugs and take additional time.

## Decision

## Consequences
