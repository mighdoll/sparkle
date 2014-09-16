### Log levels in the server code

* **TRACE** - Detailed information temporarily enabled by a developer locally. TRACE is never enabled on the main branch or on production systems.
* **DEBUG** - Developer information, enabled by default for unit and integration tests, not normally enabled on production systems.
* **INFO** - Enabled by default for production, normally log one one message per external request/event received.
* **WARN** - An unexpected condition detected in an external system, server will retry or ignore. Examples: network connection is down, an internal application sent a malformed request.
* **ERROR** - Serious unexpected condition in internal code or in an external system. No ERROR level logs are expected in production. In general, every ERROR should be investigated. Examples: unexpected internal state that indicates a bug, unrecoverable external condition (database is unresponsive after retries).

We do not use *FATAL* or *CRITICAL*.
