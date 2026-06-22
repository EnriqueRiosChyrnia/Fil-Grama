package com.filgrama.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.filgrama.repository.UserRepository;

/**
 * Carga usuarios por email desde {@link UserRepository}. El rechazo de usuarios
 * inactivos se expresa vía {@link AuthUserDetails#isEnabled()} (= {@code is_active}).
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DbUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(AuthUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
