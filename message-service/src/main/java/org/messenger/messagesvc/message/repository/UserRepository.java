package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO users (id, username)
            VALUES (:id, :username)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void insertMockUserIfAbsent(@Param("id") UUID id, @Param("username") String username);
}
