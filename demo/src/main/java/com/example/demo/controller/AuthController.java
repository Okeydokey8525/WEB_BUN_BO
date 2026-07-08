package com.example.demo.controller;

import com.example.demo.model.Branch;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("fullName") String fullName) {

        if (userRepository.findByUsername(username).isPresent()) {
            return "redirect:/register?error=username_exists";
        }

        if (!password.equals(confirmPassword)) {
            return "redirect:/register?error=password_mismatch";
        }

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElse(null);
        Branch defaultBranch = branchRepository.findAll().stream().findFirst().orElse(null);

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setFullName(fullName);
        newUser.setRole(customerRole);
        newUser.setBranch(defaultBranch);
        newUser.setEnabled(true);

        userRepository.save(newUser);

        return "redirect:/login?registered=true";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam("username") String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return "redirect:/forgot-password?error=not_found";
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);

        return "redirect:/login?reset=true";
    }
}

