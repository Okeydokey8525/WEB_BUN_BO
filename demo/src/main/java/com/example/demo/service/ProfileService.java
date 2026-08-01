package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private static final String AVATAR_PLACEHOLDER = "/images/placeholders/avatar.svg";
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void updateProfile(User user, String fullName, String phone, String address, String email, MultipartFile avatarFile) {
        String oldAvatarPath = user.getAvatarUrl();
        String newAvatarPath = null;
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        user.setEmail(email);
        if (avatarFile != null && !avatarFile.isEmpty()) {
            newAvatarPath = fileStorageService.storeImage(avatarFile, "avatars");
            user.setAvatarUrl(newAvatarPath);
        } else if (oldAvatarPath == null || oldAvatarPath.isBlank()) {
            user.setAvatarUrl(AVATAR_PLACEHOLDER);
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException ex) {
            fileStorageService.deleteIfManaged(newAvatarPath);
            throw ex;
        }
        if (newAvatarPath != null && !newAvatarPath.equals(oldAvatarPath)) {
            fileStorageService.deleteIfManaged(oldAvatarPath);
        }
    }
}
