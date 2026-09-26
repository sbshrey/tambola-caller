# Alpha14 migration evidence

Local build/native acceptance passed on 26 September 2026. This is not production approval. [Candidate validation](../../ALPHA14_VALIDATION.md) explains scope, artifacts, old/new instrumentation, failed startup/probe boundaries and remaining release work. [validation.json](validation.json) hashes the retained raw JSON/text/XML/image evidence; source helpers and narrative Markdown are versioned normally.

- `full-build.txt` is the corrected fresh 162-task / 130-test execution. `failed-first-build.txt` is retained separately.
- Final build and runtime inventories/scans cover 469 and 225 Maven versions with zero active OSV matches. Gradle's three embedded Medium findings remain unsuppressed and have a separate feature-reference assessment.
- Checksum, JDK and signature files record provenance. Audio parity and clean rebuilds identify reproducible APK/release outputs.
- `verified-layout-api30` and `verified-layout-api26` include geometry, actual screenshot-ink checks and candidate/test hashes. `api36-layout` and `api36-native-storage` record the earlier same-APK 16KB checks.
- `upgrade-api30`, `native-pair` and `process-recovery` contain the successful real update, independent native game/rematch and cold-process reconciliation witnesses.
- `preview-verification` closes the misleading full-screen-preview investigation with original pixels, enlarged crops and a negative control. No rendering workaround is shipped; earlier renderer comparison images remain as investigation data.

Raw JSON, text and XML bytes are exempt from Git newline/whitespace conversion in this directory so evidence hashes remain valid. Source files retain normal repository formatting.
