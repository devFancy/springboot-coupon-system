package dev.be.coupon.api.user.domain;

import deb.be.coupon.UserRole;
import dev.be.coupon.api.user.domain.vo.Password;
import dev.be.coupon.api.user.domain.vo.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Table(name = "users")
@Entity
public class User {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Column(name = "user_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Embedded
    private Username username;

    @Embedded
    private Password password;

    protected User() {
    }

    public User(final String username, final String password) {
        this.userRole = UserRole.USER;
        this.id = UUID.randomUUID();
        this.username = new Username(username);
        this.password = new Password(password);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username) && Objects.equals(getPassword(), user.getPassword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, getPassword());
    }

    public UUID getId() {
        return id;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public String getUsername() {
        return username.getUsername();
    }

    public String getPassword() {
        return password.getPassword();
    }
}
