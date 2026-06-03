package com.factoryapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// Dev-only security config — Spring Security form login with hardcoded in-memory users.
// In prod this is replaced by AWS Cognito OAuth2 (see application-prod.properties).
@Configuration
@Profile("dev")
public class SecurityConfig {

    // Configures form login, logout, and access rules
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/tickets", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**").permitAll()
                        .anyRequest().authenticated()  // all other routes require login
                );

        return http.build();
    }

    // Creates one in-memory user per factory role for local development
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var manager = new InMemoryUserDetailsManager();

        manager.createUser(User.withUsername("sales")
                .password(encoder.encode("test"))
                .roles("SALES").build());

        manager.createUser(User.withUsername("line")
                .password(encoder.encode("test"))
                .roles("LINE_WORKER").build());

        manager.createUser(User.withUsername("quality")
                .password(encoder.encode("test"))
                .roles("QUALITY").build());

        manager.createUser(User.withUsername("packer")
                .password(encoder.encode("test"))
                .roles("PACKER").build());

        manager.createUser(User.withUsername("shipping")
                .password(encoder.encode("test"))
                .roles("SHIPPING").build());

        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}