package com.pani.codesandbox.security;

import java.security.Permission;

/**
 * @author Pani
 * @date Created in 2024/3/9 15:57
 * @description
 */
public class DefaultSecurityManager extends SecurityManager{
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认会做所有限制");
        System.out.println(perm);
        // super.checkPermission(perm);
    }
}
