package org.example.autumn.orm.entity

import jakarta.persistence.*
import org.example.autumn.orm.entity.EntityMixin.Companion.VAR_CHAR_100
import org.example.autumn.orm.entity.EntityMixin.Companion.VAR_ENUM

@Entity
@Table(name = "api_key_auths")
class ApiKeyAuthEntity(
    /**
     * Primary key: generated api key.
     */
    @Id
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var apiKey: String,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var apiSecret: String,
    /**
     * Reference to user id.
     */
    @Column(nullable = false, updatable = false)
    var userId: Long,
    /**
     * API key expires time in milliseconds.
     */
    @Column(nullable = false, updatable = false)
    var expiresAt: Long,
) : EntityMixin {
    override fun toString(): String {
        return "ApiKeyAuthEntity(apiKey='$apiKey', apiSecret='$apiSecret', userId=$userId, expiresAt=$expiresAt)"
    }
}

@Entity
@Table(name = "password_auths")
class PasswordAuthEntity(
    /**
     * 关联至用户ID.
     */
    @Id
    @Column(nullable = false, updatable = false)
    var userId: Long,
    /**
     * 随机字符串用于创建Hmac-SHA256.
     */
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var random: String,
    /**
     * 存储HmacSHA256哈希 password = HmacSHA256(原始口令, key=random).
     */
    @Column(nullable = false, length = VAR_CHAR_100)
    var passwd: String,
) : EntityMixin


@Entity
@Table(name = "users")
class UserEntity(
    /**
     * Primary key: auto-increment long.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var type: Int,
    /**
     * Created time (milliseconds).
     */
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
) : EntityMixin {
    override fun toString(): String {
        return "UserEntity(id=$id, type=$type, createdAt=$createdAt)"
    }
}


@Entity
@Table(name = "user_profiles", uniqueConstraints = [UniqueConstraint(name = "UNI_EMAIL", columnNames = ["email"])])
class UserProfileEntity(
    /**
     * 关联至用户ID.
     */
    @Id
    @Column(nullable = false, updatable = false)
    var userId: Long,
    /**
     * 登录Email
     */
    @Column(nullable = false, updatable = false, length = VAR_CHAR_100)
    var email: String,
    @Column(nullable = false, length = VAR_CHAR_100)
    var name: String,
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
    @Column(nullable = false)
    var updatedAt: Long,
) : EntityMixin {
    override fun toString(): String {
        return "UserProfileEntity(userId=$userId, email='$email', name='$name', createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}
