package com.example.demo.controller;

import com.example.demo.dto.request.CloseShiftRequest;
import com.example.demo.dto.request.OpenShiftRequest;
import com.example.demo.dto.response.ShiftSummary;
import com.example.demo.exception.BranchAccessDeniedException;
import com.example.demo.exception.BusinessValidationException;
import com.example.demo.exception.ShiftNotOpenException;
import com.example.demo.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/cashier/shifts")
@RequiredArgsConstructor
public class CashierShiftController {

    private final ShiftService shiftService;

    @GetMapping("/current")
    public String current(Model model) {
        try {
            model.addAttribute("currentShift", shiftService.getCurrentShift());
        } catch (ShiftNotOpenException ignored) {
            model.addAttribute("currentShift", null);
        }
        model.addAttribute("openShiftRequest", new OpenShiftRequest(BigDecimal.ZERO, null));
        return "cashier/shifts";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("shifts", shiftService.getBranchShifts());
        return "cashier/shifts";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ShiftSummary shift = shiftService.getShift(id);
        model.addAttribute("shift", shift);
        model.addAttribute("closeShiftRequest", new CloseShiftRequest(id, null, null));
        return "cashier/shifts";
    }

    @PostMapping("/open")
    public String open(@Valid @ModelAttribute OpenShiftRequest request, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addValidationError(bindingResult, "openingCash", redirectAttributes);
            return "redirect:/cashier/shifts/current";
        }
        try {
            shiftService.openShift(request);
            redirectAttributes.addFlashAttribute("successMessage", "Mở ca thành công.");
        } catch (BusinessValidationException | BranchAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cashier/shifts/current";
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, @Valid @ModelAttribute CloseShiftRequest request,
                        BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addValidationError(bindingResult, "actualCash", redirectAttributes);
            return "redirect:/cashier/shifts/" + id;
        }
        if (!id.equals(request.shiftId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã ca gửi lên không khớp với đường dẫn.");
            return "redirect:/cashier/shifts/" + id;
        }
        try {
            shiftService.closeShift(new CloseShiftRequest(id, request.actualCash(), request.note()));
            redirectAttributes.addFlashAttribute("successMessage", "Đóng ca thành công.");
        } catch (BusinessValidationException | BranchAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cashier/shifts/" + id;
    }

    private void addValidationError(BindingResult bindingResult, String field, RedirectAttributes redirectAttributes) {
        var fieldError = bindingResult.getFieldError(field);
        redirectAttributes.addFlashAttribute(field + "Error", fieldError == null
                ? "Dữ liệu không hợp lệ." : fieldError.getDefaultMessage());
    }
}
