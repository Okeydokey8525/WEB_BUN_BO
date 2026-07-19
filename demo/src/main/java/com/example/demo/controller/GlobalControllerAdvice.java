package com.example.demo.controller;

import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.InvalidOrderItemStateException;
import com.example.demo.exception.InvalidOrderStateException;
import com.example.demo.exception.InvalidPaymentStateException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final UserRepository userRepository;

    @ModelAttribute("currentUser")
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userRepository.findByUsername(auth.getName()).orElse(null);
        }
        return null;
    }

    @ExceptionHandler({ResourceNotFoundException.class, BranchAccessDeniedException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", "Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.");
        return "error";
    }

    @ExceptionHandler({BusinessValidationException.class, InvalidOrderStateException.class, InvalidOrderItemStateException.class, InvalidPaymentStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusiness(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidation(Exception ex, Model model) {
        model.addAttribute("errorMessage", "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại.");
        return "error";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOptimistic(Exception ex, Model model) {
        model.addAttribute("errorMessage", "Dữ liệu vừa được một nhân viên khác cập nhật. Vui lòng tải lại trang và thử lại.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception ex, Model model) {
        log.error("Unexpected application error", ex);
        model.addAttribute("errorMessage", "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        return "error";
    }
}
