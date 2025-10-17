# emottak-edi-adapter

The adapter is an [anti corruption layer (ACL)](https://ddd-practitioners.com/home/glossary/bounded-context/bounded-context-relationship/anticorruption-layer/) between the external NHN Meldingstjener API (EDI 2.0) and our internal sevices.
This provides a stable internal interface under `/api/v1/*` so the rest of the ecosystem remains unaffected by external API
changes.

**Key Takeaways:**

* Our internal consumers use `/api/v1/*` only.
* The adapter manages all communication and error handling with NHN.
* Authentication and certificates are configured in `ediClient`.
* Metrics are collected through `PrometheusMeterRegistry`.
* Any change to the NHN API should result in updating this adapter, not the calling services.

## Purpose

Simplifies sending and receiving EDI 2.0 messages.
Isolates all network calls, parameters, and schema handling.
If NHN changes their API, only this adapter requires modification.

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

## Implementation overview

Adapter's API routes are defined in `externalRoutes` under `/api/v1`.
Each route calls the corresponding NHN endpoint.

Metrics and health checks are provided through `internalRoutes`.

## Health and metrics

| Path                         | Description                 |
|------------------------------|-----------------------------|
| `/internal/health/liveness`  | Returns “I'm alive! :)”     |
| `/internal/health/readiness` | Returns “I'm ready! :)”     |
| `/prometheus`                | Prometheus metrics endpoint |

## External API reference

The adapter wraps the NHN Meldingstjener API (EDI 2.0).
Official documentation and endpoint definitions are available at:

[utviklerportal.nhn.no - meldingstjener-api-test-internett](https://utviklerportal.nhn.no/informasjonstjenester/meldingstjener/edi-20/edi-20-ekstern-docs/openapi/meldingstjener-api-test-internett)
