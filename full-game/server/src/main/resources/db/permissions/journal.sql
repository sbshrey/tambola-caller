-- Run as owner, on the independent journal database, using existing non-member runtime roles.
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
REVOKE ALL ON DATABASE :"database" FROM :"runtime_role";
GRANT CONNECT ON DATABASE :"database" TO :"runtime_role";
REVOKE ALL ON SCHEMA public FROM :"runtime_role";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime_role";
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, :"runtime_role";
GRANT SELECT ON journal_migrations, deletion_journal_identity, profile_deletions, session_revocations TO :"runtime_role";
GRANT INSERT ON profile_deletions, session_revocations TO :"runtime_role";
GRANT UPDATE (head) ON deletion_journal_identity TO :"runtime_role";
