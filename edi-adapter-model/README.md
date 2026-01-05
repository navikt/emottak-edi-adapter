# edi-adapter-model

Shared model definitions for the EDI Adapter.

This module contains request and response objects used by both the client and the server.

## Purpose

* Define the shared contract between client and server
* Avoid duplication of model classes
* Ensure consistency across modules

## Characteristics

* No HTTP, framework, or runtime dependencies
* Pure data structures
* Used by:
    * `edi-adapter-client`
    * `edi-adapter-server`