# edi-adapter-server

The edi-adapter-server is an [anti corruption layer (ACL)](https://ddd-practitioners.com/home/glossary/bounded-context/bounded-context-relationship/anticorruption-layer/) between the external NHN Meldingstjener API (EDI 2.0) and our internal services.
It provides a stable internal interface under `/api/v1/*` so the rest of the ecosystem remains unaffected by external API changes.

Internal consumers typically interact with this API through `edi-adapter-client`.

**Key Takeaways:**

* Internal consumers use `/api/v1/*` only.
* The adapter manages all communication and error handling with NHN.
* Authentication and certificates are configured in `ediClient`.
* Metrics are collected through `PrometheusMeterRegistry`.
* Any change to the NHN API should result in updating this adapter, not the calling services.

## Purpose

* Simplifies sending and receiving EDI 2.0 messages
* Isolates all network calls, parameters, and schema handling
* Shields internal services from changes in the NHN API

## Our API (internal)

All routes exposed by this adapter are under `/api/v1`.

| Method | Path                                                      | Description                          | Calls external NHN endpoint                      |
|--------|-----------------------------------------------------------|--------------------------------------|--------------------------------------------------|
| GET    | `/api/v1/messages`                                        | Fetch messages for given receiver(s) | `GET /Messages`                                  |
| GET    | `/api/v1/messages/{messageId}`                            | Fetch a single message               | `GET /Messages/{id}`                             |
| GET    | `/api/v1/messages/{messageId}/document`                   | Download the message payload         | `GET /Messages/{id}/business-document`           |
| GET    | `/api/v1/messages/{messageId}/status`                     | Get message status                   | `GET /Messages/{id}/status`                      |
| GET    | `/api/v1/messages/{messageId}/apprec`                     | Retrieve application receipt         | `GET /Messages/{id}/apprec`                      |
| POST   | `/api/v1/messages`                                        | Send a new message                   | `POST /Messages`                                 |
| POST   | `/api/v1/messages/{messageId}/apprec/{apprecSenderHerId}` | Send application receipt             | `POST /Messages/{id}/apprec/{appRecSenderHerId}` |
| PUT    | `/api/v1/messages/{messageId}/read/{herId}`               | Mark message as read                 | `PUT /Messages/{id}/read/{herId}`                |

## API documentation (Swagger)

The EDI Adapter exposes OpenAPI/Swagger documentation for its internal API.

When running the server locally, the documentation is available at:

- `/swagger`

The Swagger UI reflects the `/api/v1/*` endpoints exposed by this service and can be used to explore and test the API locally.

Swagger is only intended for local development and internal use.

## Implementation overview

Adapter API routes are defined in `externalRoutes` under `/api/v1`.
Each route maps directly to the corresponding NHN endpoint.

Metrics and health checks are provided through `internalRoutes`.

## Health and metrics

| Path                         | Description                 |
|------------------------------|-----------------------------|
| `/internal/health/liveness`  | Returns “I'm alive! :)”     |
| `/internal/health/readiness` | Returns “I'm ready! :)”     |
| `/prometheus`                | Prometheus metrics endpoint |

## Local development

Spinning up the adapter locally involves a few simple steps:

1. Login to the NAIS Console: https://console.nav.cloud.nais.io
2. Localize the `helsemelding-nhn-edi` secret and copy the `keypair-jwk` value
3. Paste the value into `src/test/resources/keypair-jwk.json`
4. Run the adapter (typically by running the `App` class in your IDE)

When the server is running, it is curlable, for example:

`curl http://localhost:8080/api/v1/messages/{messageId}/apprec`

The adapter will POST and GET data to and from the NHN test environment in the background.

**NOTE:**  
If `NHN_KEYPAIR_PATH` is not set locally (this is typically only set in NAIS), Hoplite defaults to the test configuration defined in `application.conf`.


