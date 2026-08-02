package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeImage(MultipartFile file, String category);
    void deleteIfManaged(String publicPath);
    boolean isManagedPath(String publicPath);
}
