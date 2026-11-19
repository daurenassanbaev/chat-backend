package org.messenger.messagesvc.chat.repository;

import org.messenger.messagesvc.chat.dto.UserSummary;
import org.messenger.messagesvc.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO users (id, username)
            VALUES (:id, :username)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void insertUserIfAbsent(@Param("id") UUID id, @Param("username") String username);

    @Modifying
    @Query(value = """
            INSERT INTO users (id, username, username_verified)
            VALUES (:id, :username, true)
            ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username, username_verified = true
            WHERE NOT users.username_verified OR users.username IS DISTINCT FROM EXCLUDED.username
            """, nativeQuery = true)
    void syncUsername(UUID id, String username);

    @Query(value = """
            SELECT id, username FROM users
            WHERE username_verified AND id <> :caller AND starts_with(lower(username), lower(:prefix))
            ORDER BY lower(username), id LIMIT 20
            """, nativeQuery = true)
    List<UserSummary> search(UUID caller, String prefix);
}
