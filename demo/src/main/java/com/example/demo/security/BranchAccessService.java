package com.example.demo.security;

import com.example.demo.exception.BranchAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchAccessService {
    private static final Logger log = LoggerFactory.getLogger(BranchAccessService.class);

    private final CurrentUserService currentUserService;

    public void requireBranchAccess(Long branchId) {
        if (branchId == null) {
            deny("UNKNOWN", null, "missing branch");
        }
        if (currentUserService.isGlobalAdmin()) {
            return;
        }
        Long currentBranchId = currentUserService.requireCurrentBranchId();
        if (!currentBranchId.equals(branchId)) {
            deny("BRANCH", branchId, "cross-branch access denied");
        }
    }

    public Long requireScopedBranchId() {
        if (currentUserService.isGlobalAdmin()) {
            throw new BranchAccessDeniedException("Global admin phải chọn chi nhánh rõ ràng trước khi thao tác dữ liệu chi nhánh.");
        }
        return currentUserService.requireCurrentBranchId();
    }

    private void deny(String resourceType, Long resourceId, String reason) {
        try {
            var user = currentUserService.getCurrentUser();
            Long currentBranchId = user.getBranch() == null ? null : user.getBranch().getId();
            log.warn("Branch access denied: userId={}, username={}, currentBranchId={}, resourceType={}, resourceId={}, reason={}",
                    user.getId(), user.getUsername(), currentBranchId, resourceType, resourceId, reason);
        } catch (RuntimeException ignored) {
            log.warn("Branch access denied before current user could be resolved: resourceType={}, resourceId={}, reason={}",
                    resourceType, resourceId, reason);
        }
        throw new BranchAccessDeniedException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.");
    }
}
