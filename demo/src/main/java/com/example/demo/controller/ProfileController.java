package com.example.demo.controller;

import com.example.demo.model.Dish;
import com.example.demo.model.User;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final DishRepository dishRepository;
    private final PasswordEncoder passwordEncoder;

    private User getCurrentAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userRepository.findByUsername(auth.getName()).orElse(null);
        }
        return null;
    }

    @GetMapping("/profile")
    public String profilePage(Model model) {
        User user = getCurrentAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("favoriteDishes", user.getFavoriteDishes());
        return "customer/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "avatarUrl", required = false) String avatarUrl,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "email", required = false) String email) {

        User user = getCurrentAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        user.setFullName(fullName);
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            user.setAvatarUrl(avatarUrl.trim());
        }
        user.setPhone(phone);
        user.setAddress(address);
        user.setEmail(email);

        userRepository.save(user);
        return "redirect:/profile?success=profile_updated";
    }

    @PostMapping("/profile/password")
    public String updatePassword(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword) {

        User user = getCurrentAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return "redirect:/profile?error=old_mismatch";
        }

        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/profile?error=confirm_mismatch";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return "redirect:/profile?success=password_updated";
    }

    @PostMapping("/api/favorites/toggle")
    @ResponseBody
    public Map<String, Object> toggleFavorite(@RequestParam("dishId") Long dishId) {
        Map<String, Object> res = new HashMap<>();
        User user = getCurrentAuthenticatedUser();
        if (user == null) {
            res.put("status", "unauthenticated");
            res.put("message", "Vui lòng đăng nhập để lưu món yêu thích vào tài khoản.");
            return res;
        }

        Optional<Dish> dishOpt = user.getBranch() == null
                ? dishRepository.findById(dishId)
                : dishRepository.findByIdAndBranchId(dishId, user.getBranch().getId());
        if (dishOpt.isEmpty()) {
            res.put("status", "error");
            res.put("message", "Món ăn không tồn tại.");
            return res;
        }

        Dish dish = dishOpt.get();
        boolean isFav;
        if (user.getFavoriteDishes().contains(dish)) {
            user.getFavoriteDishes().remove(dish);
            isFav = false;
        } else {
            user.getFavoriteDishes().add(dish);
            isFav = true;
        }
        userRepository.save(user);

        res.put("status", "success");
        res.put("isFavorite", isFav);
        return res;
    }

    @GetMapping("/api/favorites/list")
    @ResponseBody
    public Map<String, Object> listFavorites() {
        Map<String, Object> res = new HashMap<>();
        User user = getCurrentAuthenticatedUser();
        if (user == null) {
            res.put("status", "unauthenticated");
            res.put("favoriteIds", Collections.emptyList());
            return res;
        }
        List<Long> ids = user.getFavoriteDishes().stream()
                .map(dish -> dish != null ? dish.getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        res.put("status", "success");
        res.put("favoriteIds", ids);
        return res;
    }
}
