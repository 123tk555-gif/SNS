package com.example.sns.user;

/** ユーザーの権限。管理者にするには、データベースで users.role を ADMIN に変える(画面からはできない)。 */
public enum Role {
    USER,
    ADMIN
}
