package com.example.demo;

import com.example.demo.exception.FileStorageException;
import com.example.demo.service.LocalFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTests {
    @TempDir Path tempDir;
    LocalFileStorageService storage;
    @BeforeEach void setUp(){ storage=new LocalFileStorageService(tempDir.toString(), 64); }
    @Test void storesValidJpeg(){ assertStored(file("original.jpg","image/jpeg",jpeg()),"menu",".jpg"); }
    @Test void storesValidPng(){ assertStored(file("original.png","image/png",png()),"avatars",".png"); }
    @Test void storesValidWebp(){ assertStored(file("original.webp","image/webp",webp()),"menu",".webp"); }
    @Test void rejectsEmptyFile(){ assertThrows(FileStorageException.class,()->storage.storeImage(file("a.jpg","image/jpeg",new byte[0]),"menu")); }
    @Test void rejectsInvalidInputs(){ assertThrows(FileStorageException.class,()->storage.storeImage(file("a.gif","image/gif",jpeg()),"menu")); assertThrows(FileStorageException.class,()->storage.storeImage(file("a.jpg","image/jpeg",png()),"menu")); assertThrows(FileStorageException.class,()->storage.storeImage(file("a.jpg","image/jpeg",jpeg()),"bad")); }
    @Test void rejectsOversized(){ assertThrows(FileStorageException.class,()->storage.storeImage(file("a.jpg","image/jpeg",new byte[65]),"menu")); }
    @Test void deletesOnlyManagedFiles() throws Exception { String path=storage.storeImage(file("a.jpg","image/jpeg",jpeg()),"menu"); Path stored=tempDir.resolve(path.substring("/uploads/".length())); assertTrue(Files.exists(stored)); storage.deleteIfManaged(path); assertFalse(Files.exists(stored)); storage.deleteIfManaged("https://example.com/a.jpg"); storage.deleteIfManaged("/images/placeholders/menu-item.svg"); storage.deleteIfManaged("/uploads/../outside"); }
    @Test void identifiesManagedPaths(){ assertTrue(storage.isManagedPath("/uploads/menu/a.jpg")); assertFalse(storage.isManagedPath("/uploads/../a.jpg")); assertFalse(storage.isManagedPath("file:///a.jpg")); }
    private void assertStored(MockMultipartFile file,String category,String extension){ String path=storage.storeImage(file,category); assertTrue(path.startsWith("/uploads/"+category+"/")); assertTrue(path.endsWith(extension)); assertFalse(path.contains(tempDir.toString())); assertFalse(path.contains("original")); assertTrue(Files.exists(tempDir.resolve(path.substring("/uploads/".length())))); }
    private MockMultipartFile file(String name,String type,byte[] bytes){ return new MockMultipartFile("file",name,type,bytes); }
    private byte[] jpeg(){ return new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1}; }
    private byte[] png(){ return new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1}; }
    private byte[] webp(){ return new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'}; }
}
