package com.example.demo.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        String redirectUrl = "/";
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (role.equals("ROLE_ADMIN")) {
                redirectUrl = "/admin/dashboard";
                break;
            } else if (role.equals("ROLE_CASHIER")) {
                redirectUrl = "/cashier/dashboard";
                break;
            } else if (role.equals("ROLE_WAITER")) {
                redirectUrl = "/waiter/dashboard";
                break;
            } else if (role.equals("ROLE_KITCHEN")) {
                redirectUrl = "/kitchen/dashboard";
                break;
            } else if (role.equals("ROLE_INVENTORY")) {
                redirectUrl = "/admin/inventory"; // Or specific inventory route
                break;
            }
        }

        response.sendRedirect(redirectUrl);
    }
}
