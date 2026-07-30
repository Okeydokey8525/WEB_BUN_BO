package com.example.demo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.example.demo.model.*; import com.example.demo.repository.*; import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.service.impl.CustomUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminAuditLogPageTests {
 @Autowired MockMvc mvc;
 @Autowired BranchRepository branches; @Autowired RoleRepository roles; @Autowired UserRepository users;
 @Autowired CustomUserDetailsService userDetailsService;
 @BeforeEach void setUp(){Branch b=new Branch();b.setName("Test audit");b.setAddress("A");b.setPhone("0");b.setStatus("ACTIVE");b=branches.save(b);users.save(account("admin-audit-page","ROLE_ADMIN",b));users.save(account("cashier-audit-page","ROLE_CASHIER",b));}
 @Test void adminRendersAuditPageAndScript() throws Exception {UserDetails admin=userDetailsService.loadUserByUsername("admin-audit-page");mvc.perform(get("/admin/audit-logs").with(user(admin))).andExpect(status().isOk()).andExpect(view().name("admin/audit-logs")).andExpect(content().string(org.hamcrest.Matchers.containsString("audit-filter"))).andExpect(content().string(org.hamcrest.Matchers.containsString("/js/admin-audit-logs.js"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Nhật ký hoạt động")));}
 @Test void staffIsForbidden() throws Exception {UserDetails cashier=userDetailsService.loadUserByUsername("cashier-audit-page");mvc.perform(get("/admin/audit-logs").with(user(cashier))).andExpect(status().isForbidden());}
 @Test void anonymousRedirectsToLogin() throws Exception {mvc.perform(get("/admin/audit-logs")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));}
 @Test void auditScriptExists() {assertTrue(new ClassPathResource("static/js/admin-audit-logs.js").exists());}
 private User account(String n,String role,Branch b){Role r=roles.findByName(role).orElseGet(()->roles.save(new Role(null,role)));User u=new User();u.setUsername(n);u.setPassword("p");u.setRole(r);u.setBranch(b);u.setEnabled(true);return u;}
}
