package com.example.demo;

import com.example.demo.model.ActivityLog;
import com.example.demo.model.Branch;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ActivityLogRepository;
import com.example.demo.repository.BranchRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogControllerTests {
    @Autowired MockMvc mockMvc; @Autowired BranchRepository branches; @Autowired RoleRepository roles; @Autowired UserRepository users; @Autowired ActivityLogRepository logs;
    private Branch branch; private User admin;
    @BeforeEach void setUp() { branch=branches.save(branch("Audit API")); admin=users.save(createUser("audit-api-admin","ROLE_ADMIN",branch)); logs.save(log(branch,"audit-api-admin","PAY_ORDER", "PAYMENT_TRANSACTION", 55L, LocalDateTime.of(2026,7,1,10,0))); }

    @Test void adminListsPagedAuditLogs() throws Exception { mockMvc.perform(get("/api/admin/audit-logs").with(user(admin.getUsername()).roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("PAY_ORDER")).andExpect(jsonPath("$.content[0].entityType").value("PAYMENT_TRANSACTION")).andExpect(jsonPath("$.content[0].entityId").value(55)).andExpect(jsonPath("$.content[0].metadata").exists()).andExpect(jsonPath("$.totalElements").value(1)); }
    @Test void adminGetsSummaryAndForwardsFilters() throws Exception { mockMvc.perform(get("/api/admin/audit-logs/summary").param("action","PAY_ORDER").param("username"," audit-api-admin ").with(user(admin.getUsername()).roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalRecords").value(1)).andExpect(jsonPath("$.branchId").value(branch.getId())); }
    @Test void staffIsForbidden() throws Exception { mockMvc.perform(get("/api/admin/audit-logs").with(user("staff").roles("CASHIER"))).andExpect(status().isForbidden()); }
    @Test void anonymousRedirectsToLoginUnderCurrentFormLoginPolicy() throws Exception { mockMvc.perform(get("/api/admin/audit-logs")).andExpect(status().is3xxRedirection()); }
    @Test void invalidActionAndRangeReturnBadRequest() throws Exception { mockMvc.perform(get("/api/admin/audit-logs").param("action","NOT_REAL").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isBadRequest()); mockMvc.perform(get("/api/admin/audit-logs").param("from","2026-07-02").param("to","2026-07-01").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isBadRequest()); }
    @Test void invalidPaginationReturnsBadRequest() throws Exception { mockMvc.perform(get("/api/admin/audit-logs").param("page","-1").with(user(admin.getUsername()).roles("ADMIN"))).andExpect(status().isBadRequest()); }
    private ActivityLog log(Branch b,String username,String action,String type,Long id,LocalDateTime at){ActivityLog l=new ActivityLog();l.setBranch(b);l.setUsername(username);l.setAction(action);l.setEntityType(type);l.setEntityId(id);l.setTimestamp(at);l.setDescription("test");l.setMetadata("{\"orderId\":55}");l.setIpAddress("SYSTEM");return l;}
    private Branch branch(String name){Branch b=new Branch();b.setName(name);b.setAddress(name);b.setPhone("0");b.setStatus("ACTIVE");return b;}
    private User createUser(String name,String roleName,Branch b){Role r=roles.findByName(roleName).orElseGet(()->roles.save(new Role(null,roleName)));User u=new User();u.setUsername(name);u.setPassword("p");u.setRole(r);u.setBranch(b);u.setEnabled(true);return u;}
}
