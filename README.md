# emottak-edi-adapter

The **emottak-edi-adapter** provides a stable internal interface wrapping the external NHN Meldingstjener API (EDI 2.0).

The adapter handles all authentication towards NHN, including [DPoP](https://utviklerportal.nhn.no/informasjonstjenester/helseid/bruksmoenstre-og-eksempelkode/bruk-av-helseid/docs/dpop/dpop_enmd), so consumers of the internal API do not need to manage authentication.

Official documentation is available [here](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/openapi/meldingstjener-api-test-v2-internett)

## Modules

| Module | Description                                        |
|---|----------------------------------------------------|
| `edi-adapter-client` | Kotlin client for calling the EDI Adapter API      |
| `edi-adapter-model` | Shared model definitions used by client and server |
| `edi-adapter-server` | EDI Adapter server exposing `/api/v1/*`            |

### edi-adapter-client
Kotlin client library for calling the EDI Adapter API from internal services.  
See: [edi-adapter-client/README.md](edi-adapter-client/README.md)

### edi-adapter-model
Shared model definitions used by both client and server.  
See: [edi-adapter-model/README.md](edi-adapter-model/README.md)

### edi-adapter-server
EDI Adapter server acting as an anti-corruption layer towards NHN and exposing `/api/v1/*`.  
See: [edi-adapter-server/README.md](edi-adapter-server/README.md)

---

Refer to each module’s README for detailed usage and implementation notes.