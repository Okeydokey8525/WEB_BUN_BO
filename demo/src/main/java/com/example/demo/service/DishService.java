package com.example.demo.service;

import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Branch;
import com.example.demo.model.Dish;
import com.example.demo.repository.DishRepository;
import com.example.demo.security.BranchAccessService;
import com.example.demo.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DishService {
    private final DishRepository dishRepository;
    private final BranchAccessService branchAccessService;
    private final CurrentUserService currentUserService;

    public List<Dish> listForCurrentBranch() {
        return dishRepository.findByBranchId(branchAccessService.requireScopedBranchId());
    }

    @Transactional
    public void toggleAvailability(Long id) {
        Dish dish = requireDishForCurrentBranch(id);
        dish.setAvailable(!dish.isAvailable());
    }

    @Transactional
    public void delete(Long id) {
        Dish dish = requireDishForCurrentBranch(id);
        dishRepository.delete(dish);
    }

    @Transactional
    public void save(Long id, String name, BigDecimal price, String category, String imageUrl) {
        Branch branch = currentUserService.requireCurrentBranch();
        Dish dish = id == null ? new Dish() : requireDishForCurrentBranch(id);
        if (id == null) {
            dish.setBranch(branch);
        }
        dish.setName(name);
        dish.setPrice(price);
        dish.setCategory(category);
        if (imageUrl != null && !imageUrl.isBlank()) {
            dish.setImageUrl(imageUrl);
        } else if (dish.getImageUrl() == null) {
            dish.setImageUrl("https://images.unsplash.com/photo-1625398407796-82650a8c135f?w=600&auto=format&fit=crop");
        }
        dish.setAvailable(true);
        dishRepository.save(dish);
    }

    private Dish requireDishForCurrentBranch(Long id) {
        return dishRepository.findByIdAndBranchId(id, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }
}
