package org.example.autumn.hello.model;

import jakarta.persistence.*;

//@Entity
//@Table(name = "users")
//data class User(
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    @Column(nullable = false, updatable = false)
//    val id: Long,
//    @Column(nullable = false, unique = true)
//    val email: String,
//    @Column(nullable = false)
//    val name: String,
//    @Column(name = "pwd_salt", nullable = false)
//    val pwdSalt: String,
//    @Column(name = "pwd_hash", nullable = false)
//    val pwdHash: String,
//) {
//    override fun toString(): String {
//        return "User(id=$id, email='$email', name='$name')"
//    }
//}

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    public long id;
    @Column(nullable = false, unique = true)
    public String email;
    @Column(nullable = false)
    public String name;
    @Column(name = "pwd_salt", nullable = false)
    public String pwdSalt;
    @Column(name = "pwd_hash", nullable = false)
    public String pwdHash;

    public User() {
    }

    public User(long id, String email, String name, String pwdSalt, String pwdHash) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.pwdSalt = pwdSalt;
        this.pwdHash = pwdHash;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}