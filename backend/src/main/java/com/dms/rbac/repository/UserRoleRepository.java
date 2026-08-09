/*
 * 鐢ㄦ埛-瑙掕壊鍏宠仈浠撳偍銆? */
package com.dms.rbac.repository;

import com.dms.rbac.entity.UserRole;
import com.dms.rbac.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 鐢ㄦ埛-瑙掕壊浠撳偍锛氭煡璇㈢敤鎴锋嫢鏈夌殑瑙掕壊鍒楄〃銆? */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    java.util.List<UserRole> findByRoleId(Long roleId);

    void deleteByUserId(Long userId);
}

