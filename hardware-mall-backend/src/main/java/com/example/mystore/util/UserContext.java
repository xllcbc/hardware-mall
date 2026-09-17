package com.example.mystore.util;

public class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> roleHolder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> userGroupHolder = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void setRole(Integer role) {
        roleHolder.set(role);
    }

    public static Integer getRole() {
        return roleHolder.get();
    }

    public static void setUserGroup(Integer userGroup) {
        userGroupHolder.set(userGroup);
    }

    public static Integer getUserGroup() {
        return userGroupHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
        roleHolder.remove();
        userGroupHolder.remove();
    }
}
