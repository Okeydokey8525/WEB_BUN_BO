package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.repository.*;
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
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProfileAvatarUploadIntegrationTests {
 @TempDir static Path root;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("app.upload-dir",()->root.toString());}
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired BranchRepository branches; @Autowired PasswordEncoder encoder;
 User alice,bob;
 @BeforeEach void setUp(){Branch b=new Branch();b.setName("Avatar");b.setAddress("A");b.setPhone("0");b.setStatus("ACTIVE");b=branches.save(b);alice=users.save(person("avatar-alice",b));bob=users.save(person("avatar-bob",b));}
 @Test void authenticatedUserUploadsValidAvatarAndPathIsRelative() throws Exception { update(alice,jpg()).andExpect(status().is3xxRedirection()); String path=users.findById(alice.getId()).orElseThrow().getAvatarUrl();assertTrue(path.startsWith("/uploads/avatars/"));assertFalse(path.contains(root.toString()));assertTrue(Files.exists(file(path))); }
 @Test void updateWithoutFileKeepsExistingAndPlaceholderRenders() throws Exception {alice.setAvatarUrl("https://example.test/old.jpg");users.saveAndFlush(alice);update(alice,null).andExpect(status().is3xxRedirection());assertEquals("https://example.test/old.jpg",users.findById(alice.getId()).orElseThrow().getAvatarUrl()); bob.setAvatarUrl(null);users.saveAndFlush(bob);mvc.perform(get("/profile").with(user("avatar-bob").roles("USER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/images/placeholders/avatar.svg")));}
 @Test void replacementDeletesManagedOldButNotRemote() throws Exception {alice.setAvatarUrl("/uploads/avatars/old.jpg");users.saveAndFlush(alice);Files.createDirectories(file(alice.getAvatarUrl()).getParent());Files.write(file(alice.getAvatarUrl()),jpeg());update(alice,jpg()).andExpect(status().is3xxRedirection());assertFalse(Files.exists(file("/uploads/avatars/old.jpg")));}
 @Test void rejectsBadFilesAndEnforcesCsrf() throws Exception {request(alice,new MockMultipartFile("avatarFile","bad.jpg","text/plain",jpeg()),true).andExpect(status().is3xxRedirection());request(alice,new MockMultipartFile("avatarFile","bad.jpg","image/jpeg",new byte[]{1}),true).andExpect(status().is3xxRedirection());request(alice,jpg(),false).andExpect(status().isForbidden());assertNull(users.findById(alice.getId()).orElseThrow().getAvatarUrl());}
 @Test void anonymousRedirectAndFormHasFileOnly() throws Exception {mvc.perform(get("/profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));mvc.perform(get("/profile").with(user("avatar-alice").roles("USER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data"))).andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"avatarFile\""))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"avatarUrl\""))));}
 private org.springframework.test.web.servlet.ResultActions update(User u,MockMultipartFile f)throws Exception{return request(u,f,true);} private org.springframework.test.web.servlet.ResultActions request(User u,MockMultipartFile f,boolean token)throws Exception{var q=multipart("/profile/update").param("fullName","Updated").param("phone","1").param("address","A").param("email","a@b.c").with(user(u.getUsername()).roles("USER"));if(f!=null)q.file(f);if(token)q.with(csrf());return mvc.perform(q);} private User person(String n,Branch b){Role r=roles.findByName("ROLE_USER").orElseGet(()->roles.save(new Role(null,"ROLE_USER")));User u=new User();u.setUsername(n);u.setPassword(encoder.encode("x"));u.setFullName(n);u.setRole(r);u.setBranch(b);u.setEnabled(true);return u;} private MockMultipartFile jpg(){return new MockMultipartFile("avatarFile","a.jpg","image/jpeg",jpeg());}private byte[] jpeg(){return new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1};}private Path file(String p){return root.resolve(p.substring("/uploads/".length()));}
}
