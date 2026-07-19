package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**")
                .ignoringRequestMatchers("/api/**")
                .ignoringRequestMatchers("/order/place", "/register", "/forgot-password", "/profile/**", "/api/favorites/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable()) // Required for H2 Console
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/menu",
                    "/about",
                    "/search",
                    "/register",
                    "/forgot-password",
                    "/order/place",
                    "/order/status/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/h2-console/**",
                    "/api/favorites/**"
                ).permitAll()
                .requestMatchers("/admin/inventory", "/admin/inventory/**").hasAnyRole("ADMIN", "INVENTORY")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/cashier/**").hasAnyRole("ADMIN", "CASHIER")
                .requestMatchers("/waiter/**").hasAnyRole("ADMIN", "WAITER")
                .requestMatchers("/kitchen/**").hasAnyRole("ADMIN", "KITCHEN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }
}
