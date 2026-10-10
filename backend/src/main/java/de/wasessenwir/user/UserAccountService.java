package de.wasessenwir.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService implements UserDetailsService {

    private final UserRepository users;

    public UserAccountService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByNormalizedUsername(Usernames.normalize(username))
                .map(AppUserPrincipal::forLogin)
                .orElseThrow(() -> new UsernameNotFoundException("Unbekannt"));
    }
}
