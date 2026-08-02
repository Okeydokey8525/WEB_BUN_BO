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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DishService {
    private static final String MENU_PLACEHOLDER = "/images/placeholders/menu-item.svg";

    private final DishRepository dishRepository;
    private final BranchAccessService branchAccessService;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

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
        String imagePath = dish.getImageUrl();
        dishRepository.delete(dish);
        dishRepository.flush();
        fileStorageService.deleteIfManaged(imagePath);
    }

    @Transactional
    public void save(Long id, String name, BigDecimal price, String category, MultipartFile imageFile) {
        Branch branch = currentUserService.requireCurrentBranch();
        boolean creating = id == null;
        Dish dish = creating ? new Dish() : requireDishForCurrentBranch(id);
        String oldImagePath = dish.getImageUrl();
        String newImagePath = null;

        if (creating) {
            dish.setBranch(branch);
        }
        dish.setName(name);
        dish.setPrice(price);
        dish.setCategory(category);
        if (imageFile != null && !imageFile.isEmpty()) {
            newImagePath = fileStorageService.storeImage(imageFile, "menu");
            dish.setImageUrl(newImagePath);
        } else if (creating) {
            dish.setImageUrl(MENU_PLACEHOLDER);
        }
        if (creating) {
            dish.setAvailable(true);
        }

        try {
            dishRepository.saveAndFlush(dish);
        } catch (RuntimeException ex) {
            fileStorageService.deleteIfManaged(newImagePath);
            throw ex;
        }

        if (newImagePath != null && !newImagePath.equals(oldImagePath)) {
            fileStorageService.deleteIfManaged(oldImagePath);
        }
    }

    private Dish requireDishForCurrentBranch(Long id) {
        return dishRepository.findByIdAndBranchId(id, branchAccessService.requireScopedBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập."));
    }
}
