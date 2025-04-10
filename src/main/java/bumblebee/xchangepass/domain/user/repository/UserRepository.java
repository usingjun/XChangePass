package bumblebee.xchangepass.domain.user.repository;

import bumblebee.xchangepass.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    @Query(value = "SELECT m FROM User m WHERE m.userEmail.value=:email")
    Optional<User> findByUserEmail(@Param("email") String email);

    @Modifying
    @Query(value = """
            DELETE FROM User u
            WHERE u.isDeleted = true
                AND u.userDeleteDate < :thirtyDaysAgo
            """)
    void deleteOldUsers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    @Query(value = """
            SELECT u
            FROM User u
            WHERE u.userId = :userId
            """)
    Optional<User> findByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT u
    FROM User u
    WHERE u.userName.value = :name
      AND u.userPhoneNumber.value = :phoneNumber
""")
    Optional<User> findByNameAndPhoneNumber(@Param("name") String name,
                                            @Param("phoneNumber") String phoneNumber);
}
