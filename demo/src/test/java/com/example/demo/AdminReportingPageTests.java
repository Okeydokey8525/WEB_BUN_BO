package com.example.demo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.example.demo.model.*; import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.service.impl.CustomUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminReportingPageTests {
 @Autowired MockMvc mvc;
 @Autowired BranchRepository branches; @Autowired RoleRepository roles; @Autowired UserRepository users;
 @Autowired CustomUserDetailsService userDetailsService;
 @BeforeEach void setUp(){Branch b=new Branch();b.setName("Test");b.setAddress("A");b.setPhone("0");b.setStatus("ACTIVE");b=branches.save(b);users.save(account("admin-page-test","ROLE_ADMIN",b));users.save(account("cashier-page-test","ROLE_CASHIER",b));}
 @Test void adminRendersReportingPageAndScript() throws Exception {UserDetails admin=userDetailsService.loadUserByUsername("admin-page-test");mvc.perform(get("/admin/reports").with(user(admin))).andExpect(status().isOk()).andExpect(view().name("admin/reports")).andExpect(content().string(org.hamcrest.Matchers.containsString("report-filter"))).andExpect(content().string(org.hamcrest.Matchers.containsString("/js/admin-reports.js"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Báo cáo")));}
 @Test void staffIsForbidden() throws Exception {UserDetails cashier=userDetailsService.loadUserByUsername("cashier-page-test");mvc.perform(get("/admin/reports").with(user(cashier))).andExpect(status().isForbidden());}
 @Test void anonymousRedirectsToLogin() throws Exception {mvc.perform(get("/admin/reports")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));}
 @Test void reportScriptExists() {assertTrue(new ClassPathResource("static/js/admin-reports.js").exists());}
 private User account(String n,String role,Branch b){Role r=roles.findByName(role).orElseGet(()->roles.save(new Role(null,role)));User u=new User();u.setUsername(n);u.setPassword("p");u.setRole(r);u.setBranch(b);u.setEnabled(true);return u;}
}
