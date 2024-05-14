package org.example.autumn.hello;

import jakarta.persistence.*;

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