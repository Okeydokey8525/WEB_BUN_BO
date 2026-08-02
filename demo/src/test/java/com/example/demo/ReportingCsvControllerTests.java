package com.example.demo;

import com.example.demo.model.Branch;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ReportingCsvControllerTests {
    @Autowired MockMvc mvc; @Autowired BranchRepository branches; @Autowired RoleRepository roles; @Autowired UserRepository users;
    private Branch branch; private User admin;
    @BeforeEach void setUp(){branch=branches.save(branch("CSV Huế"));admin=users.save(createUser("csv-admin","ROLE_ADMIN",branch));}
    @Test void adminExportsAllSixCsvReportsWithBomAndAttachment() throws Exception { for(String path:List.of("revenue/export.csv","daily-revenue/export.csv","payment-methods/export.csv","top-dishes/export.csv","inventory-consumption/export.csv","shifts/export.csv")){byte[] body=mvc.perform(get("/api/admin/reports/"+path).param("from","2026-07-01").param("to","2026-07-02").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment"))).andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse().getContentAsByteArray();assertEquals((byte)0xEF,body[0]);assertEquals((byte)0xBB,body[1]);assertEquals((byte)0xBF,body[2]);} }
    @Test void staffForbiddenAndAnonymousRedirectsUnderFormLogin() throws Exception {mvc.perform(get("/api/admin/reports/revenue/export.csv").param("from","2026-07-01").param("to","2026-07-02").with(user("staff").roles("CASHIER"))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/reports/revenue/export.csv").param("from","2026-07-01").param("to","2026-07-02")).andExpect(status().is3xxRedirection());}
    @Test void invalidDatesAndLimitsAreRejected() throws Exception {mvc.perform(get("/api/admin/reports/revenue/export.csv").param("from","2026-07-03").param("to","2026-07-02").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isBadRequest());mvc.perform(get("/api/admin/reports/top-dishes/export.csv").param("from","2026-07-01").param("to","2026-07-02").param("limit","0").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isBadRequest());}
    @Test void branchMismatchIsHidden() throws Exception {mvc.perform(get("/api/admin/reports/revenue/export.csv").param("from","2026-07-01").param("to","2026-07-02").param("branchId","999999").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isNotFound());}
    private Branch branch(String name){Branch b=new Branch();b.setName(name);b.setAddress(name);b.setPhone("0");b.setStatus("ACTIVE");return b;}
    private User createUser(String name,String role,Branch branch){Role r=roles.findByName(role).orElseGet(()->roles.save(new Role(null,role)));User u=new User();u.setUsername(name);u.setPassword("p");u.setRole(r);u.setBranch(branch);u.setEnabled(true);return u;}
}
