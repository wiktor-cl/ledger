package com.wiktorcl.ledger.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public JwtService.GeneratedToken register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyTakenException(username);
        }
        AppUser user = new AppUser(username, passwordEncoder.encode(rawPassword), Role.USER);
        userRepository.save(user);
        return jwtService.generate(user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public JwtService.GeneratedToken login(String username, String rawPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return jwtService.generate(user.getUsername(), user.getRole());
    }
}
