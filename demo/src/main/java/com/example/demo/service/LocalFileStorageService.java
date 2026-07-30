package com.example.demo.service;

import com.example.demo.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Set<String> CATEGORIES = Set.of("menu", "avatars");
    private static final Map<String, byte[]> SIGNATURES = Map.of("jpg", new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF}, "png", new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});
    private final Path root; private final long maxBytes;
    public LocalFileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir, @Value("${app.upload.max-bytes:5242880}") long maxBytes) { this.root=Paths.get(uploadDir).toAbsolutePath().normalize(); this.maxBytes=maxBytes; }
    public String storeImage(MultipartFile file, String category) {
        if(file==null||file.isEmpty()) throw new FileStorageException("Tệp ảnh không được để trống."); if(!CATEGORIES.contains(category)) throw new FileStorageException("Danh mục upload không hợp lệ."); if(file.getSize()>maxBytes) throw new FileStorageException("Tệp ảnh vượt quá dung lượng cho phép.");
        String original=Optional.ofNullable(file.getOriginalFilename()).orElse(""); String ext=original.contains(".")?original.substring(original.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):""; if(!Set.of("jpg","jpeg","png","webp").contains(ext)) throw new FileStorageException("Định dạng ảnh không hợp lệ.");
        try { byte[] bytes=file.getBytes(); if(!valid(bytes,ext)) throw new FileStorageException("Nội dung tệp không phải ảnh hợp lệ."); Path dir=root.resolve(category).normalize(); if(!dir.startsWith(root)) throw new FileStorageException("Đường dẫn upload không hợp lệ."); Files.createDirectories(dir); String name=UUID.randomUUID()+"."+ext; Files.write(dir.resolve(name),bytes,StandardOpenOption.CREATE_NEW); return "/uploads/"+category+"/"+name; } catch(IOException e){ throw new FileStorageException("Không thể lưu ảnh.",e); }
    }
    public boolean isManagedPath(String path){ return path!=null&&path.startsWith("/uploads/")&&!path.contains(".."); }
    public void deleteIfManaged(String path){ if(!isManagedPath(path)) return; Path target=root.resolve(path.substring("/uploads/".length())).normalize(); if(target.startsWith(root)) try{Files.deleteIfExists(target);}catch(IOException ignored){} }
    private boolean valid(byte[] b,String ext){ if(b.length<3)return false; if("webp".equals(ext)) return b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P'; byte[] s=SIGNATURES.get("jpeg".equals(ext)?"jpg":ext); if(s==null||b.length<s.length)return false; for(int i=0;i<s.length;i++)if(b[i]!=s[i])return false; return true; }
}
