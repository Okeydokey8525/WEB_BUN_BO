package com.example.demo.security;

import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Branch;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BranchAccessDeniedException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng hiện tại."));
    }

    public Optional<Branch> getCurrentBranch() {
        return Optional.ofNullable(getCurrentUser().getBranch());
    }

    public Branch requireCurrentBranch() {
        return getCurrentBranch()
                .orElseThrow(() -> new BranchAccessDeniedException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }

    public Long requireCurrentBranchId() {
        return requireCurrentBranch().getId();
    }

    public boolean isGlobalAdmin() {
        User user = getCurrentUser();
        return user.getBranch() == null
                && user.getRole() != null
                && "ROLE_SUPER_ADMIN".equals(user.getRole().getName());
    }
}
