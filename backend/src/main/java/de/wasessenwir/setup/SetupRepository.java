package de.wasessenwir.setup;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SetupRepository {

    private final JdbcClient jdbc;

    public SetupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void lockInstallation() {
        jdbc.sql("""
                with held as (select pg_advisory_xact_lock(83422109))
                select 1
                """)
                .query(Integer.class)
                .single();
    }

    public Optional<SetupState> find() {
        return jdbc.sql("""
                select token_hash, expires_at, completed_at, created_at, updated_at
                from app_setup
                where id = 1
                """)
                .query(this::map)
                .optional();
    }

    public SetupState lock() {
        return jdbc.sql("""
                select token_hash, expires_at, completed_at, created_at, updated_at
                from app_setup
                where id = 1
                for update
                """)
                .query(this::map)
                .optional()
                .orElseThrow(() -> new IllegalStateException("Der Einrichtungsdatensatz fehlt."));
    }

    public void insertOpen(byte[] tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        jdbc.sql("""
                insert into app_setup (id, token_hash, expires_at, completed_at, created_at, updated_at)
                values (1, :tokenHash, :expiresAt, null, :now, :now)
                """)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .param("now", now)
                .update();
    }

    public boolean replaceOpenToken(byte[] tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        return jdbc.sql("""
                update app_setup
                set token_hash = :tokenHash, expires_at = :expiresAt, updated_at = :now
                where id = 1 and completed_at is null
                """)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .param("now", now)
                .update() == 1;
    }

    public boolean markCompleted(OffsetDateTime completedAt) {
        return jdbc.sql("""
                update app_setup
                set token_hash = null, completed_at = :completedAt, updated_at = :completedAt
                where id = 1 and completed_at is null
                """)
                .param("completedAt", completedAt)
                .update() == 1;
    }

    private SetupState map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SetupState(
                rs.getBytes("token_hash"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
