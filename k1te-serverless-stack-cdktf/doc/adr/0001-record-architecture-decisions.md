# 1. Record architecture decisions

Date: 2023-08-11

## Status

Accepted

## Context

We need to record the architectural decisions made on this project.

## Facing

It is more structured way than maintain TODO section in the README.md

## Decision

We will use Architecture Decision Records, as described by Michael Nygard in this article:

We considered original format described here

http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions

But finally chosen Y statements format described here as more complete

https://medium.com/olzzio/y-statements-10eb07b5a177

Each template element appears on one line in the above example:

1. Context: functional requirement (story, use case) or arch. component,
2. Facing: non-functional requirement, for instance a desired quality,
3. We decided: decision outcome (arguably the most important part),
4. and neglected alternatives not chosen (not to be forgotten!),
5. to achieve: benefits, the full or partial satisfaction of requirement(s),
6. accepting that: drawbacks and other consequences, for instance impact on other properties/context and effort/cost (both short term and long term).

## Consequences

More structured way to track architecture decisions
