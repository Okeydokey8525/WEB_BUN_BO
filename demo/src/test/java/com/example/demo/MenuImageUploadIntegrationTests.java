package com.example.demo;

import com.example.demo.model.Branch;
import com.example.demo.model.Dish;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.DishRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MenuImageUploadIntegrationTests {
    private static final String PLACEHOLDER = "/images/placeholders/menu-item.svg";

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private BranchRepository branchRepository;
    @Autowired private DishRepository dishRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FileStorageService fileStorageService;

    private Branch branch;

    @BeforeEach
    void setUp() {
        branch = branchRepository.save(branch());
        userRepository.save(createUser("menu-upload-admin", "ROLE_ADMIN"));
        userRepository.save(createUser("menu-upload-cashier", "ROLE_CASHIER"));
    }

    @Test
    void adminCreatesDishWithValidImage() throws Exception {
        saveNewDish("Uploaded dish", image("dish.jpg", "image/jpeg", jpeg()))
                .andExpect(status().is3xxRedirection());

        Dish dish = dishByName("Uploaded dish");
        assertTrue(dish.getImageUrl().startsWith("/uploads/menu/"));
        assertTrue(Files.exists(storedFile(dish.getImageUrl())));
    }

    @Test
    void adminCreatesDishWithoutImageUsesPlaceholder() throws Exception {
        saveNewDish("Placeholder dish", null).andExpect(status().is3xxRedirection());
        assertEquals(PLACEHOLDER, dishByName("Placeholder dish").getImageUrl());
    }

    @Test
    void adminUpdatesDishWithoutNewImageKeepsOldPath() throws Exception {
        Dish dish = dish("Keep image", "/uploads/menu/existing.jpg");
        Files.createDirectories(storedFile(dish.getImageUrl()).getParent());
        Files.write(storedFile(dish.getImageUrl()), jpeg());

        updateDish(dish, "Keep image changed", null).andExpect(status().is3xxRedirection());
        assertEquals("/uploads/menu/existing.jpg", dishRepository.findById(dish.getId()).orElseThrow().getImageUrl());
        assertTrue(Files.exists(storedFile("/uploads/menu/existing.jpg")));
    }

    @Test
    void replacingImageDeletesOldManagedFile() throws Exception {
        Dish dish = dish("Replace image", "/uploads/menu/old.jpg");
        Files.createDirectories(storedFile(dish.getImageUrl()).getParent());
        Files.write(storedFile(dish.getImageUrl()), jpeg());

        updateDish(dish, "Replace image", image("new.png", "image/png", png()))
                .andExpect(status().is3xxRedirection());

        Dish updated = dishRepository.findById(dish.getId()).orElseThrow();
        assertTrue(updated.getImageUrl().startsWith("/uploads/menu/"));
        assertFalse(Files.exists(storedFile("/uploads/menu/old.jpg")));
        assertTrue(Files.exists(storedFile(updated.getImageUrl())));
    }

    @Test
    void replacingRemoteImageDoesNotTreatExternalUrlAsManagedFile() throws Exception {
        Dish dish = dish("Remote image", "https://example.test/legacy.jpg");
        updateDish(dish, "Remote image", image("new.jpg", "image/jpeg", jpeg()))
                .andExpect(status().is3xxRedirection());
        assertTrue(dishRepository.findById(dish.getId()).orElseThrow().getImageUrl().startsWith("/uploads/menu/"));
    }

    @Test
    void invalidMimeSignatureAndSizeAreRejectedWithoutCreatingDish() throws Exception {
        saveNewDish("Bad mime", image("bad.jpg", "text/plain", jpeg())).andExpect(status().is3xxRedirection());
        saveNewDish("Bad signature", image("bad.jpg", "image/jpeg", png())).andExpect(status().is3xxRedirection());
        saveNewDish("Too large", image("large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]))
                .andExpect(status().is3xxRedirection());

        assertTrue(dishRepository.findByBranchId(branch.getId()).isEmpty());
    }

    @Test
    void deletingDishDeletesManagedFileButNotPlaceholder() throws Exception {
        Dish managed = dish("Managed delete", "/uploads/menu/delete.jpg");
        Files.createDirectories(storedFile(managed.getImageUrl()).getParent());
        Files.write(storedFile(managed.getImageUrl()), jpeg());
        Dish placeholder = dish("Placeholder delete", PLACEHOLDER);

        mockMvc.perform(post("/admin/menu/delete/{id}", managed.getId()).with(user("menu-upload-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/menu/delete/{id}", placeholder.getId()).with(user("menu-upload-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertFalse(Files.exists(storedFile("/uploads/menu/delete.jpg")));
        assertFalse(fileStorageService.isManagedPath(PLACEHOLDER));
    }

    @Test
    void cashierCannotSaveAndAnonymousUserIsRedirected() throws Exception {
        saveNewDishAs("menu-upload-cashier", "CASHIER", "Forbidden", image("dish.jpg", "image/jpeg", jpeg()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/menu/save")
                        .file(image("dish.jpg", "image/jpeg", jpeg()))
                        .param("name", "Anonymous")
                        .param("price", "50000")
                        .param("category", "Bún Bò")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void menuFormUsesMultipartImageInputAndNoEditableImageUrl() throws Exception {
        mockMvc.perform(get("/admin/menu").with(user("menu-upload-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"imageFile\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"imageUrl\""))));
    }

    private org.springframework.test.web.servlet.ResultActions saveNewDish(String name, MockMultipartFile image) throws Exception {
        return saveNewDishAs("menu-upload-admin", "ADMIN", name, image);
    }

    private org.springframework.test.web.servlet.ResultActions saveNewDishAs(String username, String role, String name, MockMultipartFile image) throws Exception {
        var request = multipart("/admin/menu/save")
                .param("name", name).param("price", "50000").param("category", "Bún Bò")
                .with(user(username).roles(role)).with(csrf());
        if (image != null) request.file(image);
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions updateDish(Dish dish, String name, MockMultipartFile image) throws Exception {
        var request = multipart("/admin/menu/save")
                .param("id", dish.getId().toString()).param("name", name).param("price", "60000").param("category", "Bún Bò")
                .with(user("menu-upload-admin").roles("ADMIN")).with(csrf());
        if (image != null) request.file(image);
        return mockMvc.perform(request);
    }

    private Dish dishByName(String name) {
        return dishRepository.findByBranchId(branch.getId()).stream()
                .filter(dish -> name.equals(dish.getName())).findFirst().orElseThrow();
    }

    private Dish dish(String name, String imagePath) {
        Dish dish = new Dish();
        dish.setBranch(branch); dish.setName(name); dish.setPrice(new BigDecimal("50000"));
        dish.setCategory("Bún Bò"); dish.setImageUrl(imagePath); dish.setAvailable(true);
        return dishRepository.saveAndFlush(dish);
    }

    private Path storedFile(String publicPath) {
        return uploadDir.resolve(publicPath.substring("/uploads/".length()));
    }

    private Branch branch() {
        Branch value = new Branch();
        value.setName("Menu Upload Branch"); value.setAddress("Address"); value.setPhone("000"); value.setStatus("ACTIVE");
        return value;
    }

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(null, roleName)));
        User value = new User();
        value.setUsername(username); value.setPassword(passwordEncoder.encode("password")); value.setFullName(username);
        value.setRole(role); value.setBranch(branch); value.setEnabled(true);
        return value;
    }

    private MockMultipartFile image(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("imageFile", name, contentType, bytes);
    }

    private byte[] jpeg() { return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}; }
    private byte[] png() { return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1}; }
}
