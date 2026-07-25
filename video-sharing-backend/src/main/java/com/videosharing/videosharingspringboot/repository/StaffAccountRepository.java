package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {
    Optional<StaffAccount> findByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    List<StaffAccount> findAllByRoleOrderByUsernameAsc(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffAccount s where s.username = :username")
    Optional<StaffAccount> findByUsernameForUpdate(@Param("username") String username);
}
