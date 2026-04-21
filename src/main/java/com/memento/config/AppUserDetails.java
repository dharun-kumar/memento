package com.memento.config;

import com.memento.model.Role;
import com.memento.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Wraps our User entity into Spring Security's UserDetails contract.
// Spring Security never touches the User entity directly — it only knows AppUserDetails.
// This separation keeps the security layer decoupled from the persistence layer.
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Role role;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.role = user.getRole();
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return password; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security's hasRole("ADMIN") internally checks for "ROLE_ADMIN".
        // Our enum stores clean names (ADMIN, OPERATOR, GUEST) — the prefix is added only here.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // The four methods below are part of the UserDetails contract for account lifecycle.
    // All return true because we have no account locking, expiry, or disabling logic yet.
    // Override these (and add the matching DB columns) if you add that feature later.

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }

}
