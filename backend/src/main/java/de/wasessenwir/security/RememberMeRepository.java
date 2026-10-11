package de.wasessenwir.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RememberMeRepository {

    private final JdbcClient jdbc;

    RememberMeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<RememberMeToken> lock(String selector) {
        return jdbc.sql("""
                select selector, user_id, token_hash, previous_token_hash, rotated_at, expires_at, created_at
                from remember_me_token
                where selector = :selector
                for update
                """)
                .param("selector", selector)
                .query(this::map)
                .optional();
    }

    void insert(String selector, UUID userId, byte[] tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        jdbc.sql("""
                insert into remember_me_token (
                    selector, user_id, token_hash, previous_token_hash, rotated_at, expires_at, created_at
                ) values (
                    :selector, :userId, :tokenHash, null, null, :expiresAt, :now
                )
                """)
                .param("selector", selector)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .param("now", now)
                .update();
    }

    void replace(String selector, UUID userId, byte[] tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        jdbc.sql("""
                update remember_me_token
                set previous_token_hash = token_hash,
                    token_hash = :tokenHash,
                    rotated_at = :now,
                    expires_at = :expiresAt,
                    created_at = :now
                where selector = :selector
                  and user_id = :userId
                """)
                .param("selector", selector)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .param("now", now)
                .update();
    }

    void rotate(String selector, byte[] tokenHash, OffsetDateTime rotatedAt) {
        jdbc.sql("""
                update remember_me_token
                set previous_token_hash = token_hash,
                    token_hash = :tokenHash,
                    rotated_at = :rotatedAt
                where selector = :selector
                """)
                .param("selector", selector)
                .param("tokenHash", tokenHash)
                .param("rotatedAt", rotatedAt)
                .update();
    }

    void deleteBySelector(String selector) {
        jdbc.sql("delete from remember_me_token where selector = :selector")
                .param("selector", selector)
                .update();
    }

    void deleteByUser(UUID userId) {
        jdbc.sql("delete from remember_me_token where user_id = :userId")
                .param("userId", userId)
                .update();
    }

    void deleteExpired(OffsetDateTime now) {
        jdbc.sql("delete from remember_me_token where expires_at <= :now")
                .param("now", now)
                .update();
    }

    void trimTo(UUID userId, int keep) {
        jdbc.sql("""
                delete from remember_me_token
                where user_id = :userId
                  and selector in (
                    select selector
                    from remember_me_token
                    where user_id = :userId
                    order by created_at desc, selector desc
                    offset :keep
                  )
                """)
                .param("userId", userId)
                .param("keep", keep)
                .update();
    }

    private RememberMeToken map(ResultSet rs, int row) throws SQLException {
        return new RememberMeToken(
                rs.getString("selector"),
                rs.getObject("user_id", UUID.class),
                rs.getBytes("token_hash"),
                rs.getBytes("previous_token_hash"),
                rs.getObject("rotated_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
