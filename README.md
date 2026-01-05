# emottak-edi-adapter

The **emottak-edi-adapter** provides a stable internal interface towards the external NHN Meldingstjener API (EDI 2.0).

## Modules

| Module |
|---|
| `edi-adapter-client` |
| `edi-adapter-model` |
| `edi-adapter-server` |

### edi-adapter-client
Kotlin client library for calling the EDI Adapter API from internal services.  
See: [edi-adapter-client/README.md](edi-adapter-client/README.md)

### edi-adapter-model
Shared model definitions used by both client and server.  
See: [edi-adapter-model/README.md](edi-adapter-model/README.md)

### edi-adapter-server
EDI Adapter service acting as an anti-corruption layer towards NHN and exposing `/api/v1/*`.  
See: [edi-adapter-server/README.md](edi-adapter-server/README.md)

---

Refer to each module’s README for detailed usage and implementation notes.