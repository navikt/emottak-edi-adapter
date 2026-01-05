# edi-adapter-client

Kotlin client library for interacting with the EDI Adapter API.

The client wraps the HTTP endpoints exposed by `edi-adapter-server` and provides a typed, idiomatic API for internal services.

## Purpose

* Hide HTTP, serialization, and error handling from consumers
* Provide a stable, strongly typed client API
* Simplify integration with the EDI Adapter

## Usage

Internal services should depend on this module rather than calling the EDI Adapter HTTP API directly.

The client communicates exclusively with the adapter’s internal API under `/api/v1/*`.

## Relationship to other modules

* Uses shared models from `edi-adapter-model`
* Calls the HTTP API exposed by `edi-adapter-server`