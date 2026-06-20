package com.factoryapp.config;

import jakarta.servlet.http.HttpServletResponse;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("http://localhost:3000/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .permitAll()
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/assets/**", "/index.html", "/",
                    "/*.js", "/*.css", "/*.ico", "/*.svg", "/*.png"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"unauthorized\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            );
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername("sales").password(encoder.encode("test")).roles("SALES").build());
        manager.createUser(User.withUsername("line").password(encoder.encode("test")).roles("LINE_WORKER").build());
        manager.createUser(User.withUsername("quality").password(encoder.encode("test")).roles("QUALITY").build());
        manager.createUser(User.withUsername("packer").password(encoder.encode("test")).roles("PACKER").build());
        manager.createUser(User.withUsername("shipping").password(encoder.encode("test")).roles("SHIPPING").build());
        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
