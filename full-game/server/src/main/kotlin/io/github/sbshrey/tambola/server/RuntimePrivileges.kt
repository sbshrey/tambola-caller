package io.github.sbshrey.tambola.server

/** Startup checks effective table/column grants, including PUBLIC, before opening HTTP. */
internal object RuntimePrivileges {
    private data class Access(val writes: Set<String> = emptySet(), val updateColumns: Set<String> = emptySet())
    private val primary = mapOf(
        "schema_migrations" to Access(), "guests" to Access(setOf("INSERT", "UPDATE", "DELETE")),
        "rooms" to Access(setOf("INSERT", "UPDATE", "DELETE")), "create_receipts" to Access(setOf("INSERT")),
        "command_receipts" to Access(setOf("INSERT", "DELETE"), setOf("response")),
        "room_events" to Access(setOf("INSERT", "DELETE")), "rate_limits" to Access(setOf("INSERT", "DELETE"), setOf("requests")),
        "finished_rounds" to Access(setOf("INSERT"), setOf("payload")), "room_participants" to Access(setOf("INSERT", "DELETE")),
        "deletion_receipts" to Access(setOf("INSERT", "DELETE")), "deletion_recovery" to Access(updateColumns = setOf("journal_id", "applied_sequence")),
        "coin_wallets" to Access(setOf("INSERT"), setOf("refill_after")), "coin_ledger" to Access(setOf("INSERT")),
        "reward_ad_intents" to Access(setOf("INSERT")), "reward_ad_receipts" to Access(setOf("INSERT")),
        "bingo_rooms" to Access(setOf("INSERT", "UPDATE", "DELETE")),
        "bingo_participants" to Access(setOf("INSERT", "DELETE")),
        "bingo_receipts" to Access(setOf("INSERT"), setOf("response")),
        "match_receipts" to Access(setOf("INSERT"), setOf("response")),
    )
    private val recovery = mapOf("journal_migrations" to Access(), "profile_deletions" to Access(setOf("INSERT")),
        "session_revocations" to Access(setOf("INSERT")),
        "deletion_journal_identity" to Access(updateColumns = setOf("head")))

    fun verify(database: Database, journal: Boolean) = database.transaction { connection ->
        fun flag(sql: String, vararg params: Any?) = connection.query(sql, *params) { it.getBoolean(1) }.single()
        check(!flag("""SELECT current_user <> session_user OR EXISTS(SELECT 1 FROM pg_roles r
            WHERE (r.rolname = current_user AND (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls))
            OR (r.rolname <> current_user AND pg_has_role(current_user, r.oid, 'MEMBER')))""")) {
            "Runtime database credentials must not have administrative privileges or role memberships"
        }
        check(!flag("""SELECT has_database_privilege(current_database(), 'CREATE')
            OR has_database_privilege(current_database(), 'TEMP')
            OR has_database_privilege(current_database(), 'CONNECT WITH GRANT OPTION')
            OR EXISTS(SELECT 1 FROM pg_namespace n WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema'
                AND (has_schema_privilege(n.oid, 'CREATE') OR has_schema_privilege(n.oid, 'USAGE WITH GRANT OPTION') OR pg_has_role(current_user, n.nspowner, 'MEMBER')))
            OR EXISTS(SELECT 1 FROM pg_database d WHERE d.datname = current_database() AND pg_has_role(current_user, d.datdba, 'MEMBER'))
            OR EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = current_schema() AND pg_has_role(current_user, c.relowner, 'MEMBER'))""")) {
            "Runtime database credentials must not own, create or delegate database objects or temporary tables"
        }
        val expected = if (journal) recovery else primary
        val tables = connection.query("SELECT tablename FROM pg_tables WHERE schemaname = current_schema()") { it.getString(1) }
        check(tables.containsAll(expected.keys)) { "Required runtime tables are missing" }
        tables.forEach { table ->
            val access = expected[table]
            val allowed = access?.writes.orEmpty() + if (access != null) setOf("SELECT") else emptySet()
            listOf("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "TRIGGER", "REFERENCES").forEach { privilege ->
                check(flag("SELECT has_table_privilege(?, ?)", table, privilege) == (privilege in allowed)) {
                    "Runtime credentials have missing or excessive table privileges"
                }
                check(!flag("SELECT has_table_privilege(?, ?)", table, "$privilege WITH GRANT OPTION")) {
                    "Runtime credentials must not delegate table privileges"
                }
            }
            val columns = connection.query("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?", table) { it.getString(1) }
            columns.forEach { column ->
                listOf("SELECT", "INSERT", "UPDATE", "REFERENCES").forEach { privilege ->
                    val required = privilege in allowed || (privilege == "UPDATE" && column in access?.updateColumns.orEmpty())
                    check(flag("SELECT has_column_privilege(?, ?, ?)", table, column, privilege) == required) {
                        "Runtime credentials have missing or excessive column privileges"
                    }
                    check(!flag("SELECT has_column_privilege(?, ?, ?)", table, column, "$privilege WITH GRANT OPTION")) {
                        "Runtime credentials must not delegate column privileges"
                    }
                }
            }
        }
    }
}
