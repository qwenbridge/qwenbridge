# Contributing

## Requirements

- Java 21
- Maven 3.9+

## Local workflow

Run tests before opening a pull request:

    mvn clean test

## V1 scope rules

V1 is only for search-box intelligence.

Do not add:

- Database
- Product catalog
- Forms
- Payments
- API Gateway behavior

## Architecture rules

- Each pipeline step produces one result object.
- ExecutionContext is a typed result store.
- Avoid God Objects.
- Every final decision must be one of ALLOW, REWRITE, CLARIFY, or BLOCK.
