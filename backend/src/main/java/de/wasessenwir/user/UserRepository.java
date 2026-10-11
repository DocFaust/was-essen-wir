package de.wasessenwir.user;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<StoredUser> MAPPER = (rs, row) -> new StoredUser(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("username_normalized"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getBoolean("active"),
            rs.getBoolean("password_change_required"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredUser> findById(UUID id) {
        return jdbc.sql("select * from app_user where id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public Optional<StoredUser> findByNormalizedUsername(String usernameNormalized) {
        return jdbc.sql("select * from app_user where username_normalized = :username")
                .param("username", usernameNormalized)
                .query(MAPPER)
                .optional();
    }

    public List<StoredUser> findAll() {
        return jdbc.sql("select * from app_user order by lower(display_name), username_normalized")
                .query(MAPPER)
                .list();
    }

    public List<StoredUser> findActive() {
        return jdbc.sql("""
                select * from app_user
                where active
                order by lower(display_name), id
                """)
                .query(MAPPER)
                .list();
    }

    public List<StoredUser> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select * from app_user where id in (:ids)")
                .param("ids", ids)
                .query(MAPPER)
                .list();
    }

    public List<UUID> findActiveIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select id from app_user where active and id in (:ids)")
                .param("ids", ids)
                .query((rs, row) -> rs.getObject("id", UUID.class))
                .list();
    }

    public void insert(StoredUser user) {
        jdbc.sql("""
                insert into app_user (
                    id, username, username_normalized, display_name, password_hash, role, active,
                    password_change_required, created_at, updated_at
                ) values (
                    :id, :username, :usernameNormalized, :displayName, :passwordHash, :role, :active,
                    :passwordChangeRequired, :createdAt, :updatedAt
                )
                """)
                .param("id", user.id())
                .param("username", user.username())
                .param("usernameNormalized", user.usernameNormalized())
                .param("displayName", user.displayName())
                .param("passwordHash", user.passwordHash())
                .param("role", user.role().name())
                .param("active", user.active())
                .param("passwordChangeRequired", user.passwordChangeRequired())
                .param("createdAt", user.createdAt())
                .param("updatedAt", user.updatedAt())
                .update();
    }

    public void updateProfile(UUID id, String displayName, OffsetDateTime updatedAt) {
        jdbc.sql("""
                update app_user
                set display_name = :displayName, updated_at = :updatedAt
                where id = :id
                """)
                .param("id", id)
                .param("displayName", displayName)
                .param("updatedAt", updatedAt)
                .update();
    }

    public void updateAccount(UUID id, String displayName, Role role, boolean active, OffsetDateTime updatedAt) {
        jdbc.sql("""
                update app_user
                set display_name = :displayName, role = :role, active = :active, updated_at = :updatedAt
                where id = :id
                """)
                .param("id", id)
                .param("displayName", displayName)
                .param("role", role.name())
                .param("active", active)
                .param("updatedAt", updatedAt)
                .update();
    }

    public void updatePassword(UUID id, String passwordHash, boolean passwordChangeRequired, OffsetDateTime updatedAt) {
        jdbc.sql("""
                update app_user
                set password_hash = :passwordHash,
                    password_change_required = :passwordChangeRequired,
                    updated_at = :updatedAt
                where id = :id
                """)
                .param("id", id)
                .param("passwordHash", passwordHash)
                .param("passwordChangeRequired", passwordChangeRequired)
                .param("updatedAt", updatedAt)
                .update();
    }

    public int count() {
        Long count = jdbc.sql("select count(*) from app_user").query(Long.class).single();
        return count == null ? 0 : count.intValue();
    }

    public void lockAccounts() {
        jdbc.sql("""
                with held as (select pg_advisory_xact_lock(83422110))
                select 1
                """)
                .query(Integer.class)
                .single();
    }

    public int countActiveAdmins() {
        Long count = jdbc.sql("select count(*) from app_user where role = 'ADMIN' and active")
                .query(Long.class)
                .single();
        return count == null ? 0 : count.intValue();
    }

    public Optional<StoredUser> lockById(UUID id) {
        return jdbc.sql("select * from app_user where id = :id for update")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }
}
