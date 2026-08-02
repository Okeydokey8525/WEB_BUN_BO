package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.model.enums.ShiftStatus;
import com.example.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ShiftReportingRepositoryIntegrationTests {
    @Autowired BranchRepository branchRepository; @Autowired UserRepository userRepository; @Autowired RoleRepository roleRepository; @Autowired WorkShiftRepository workShiftRepository;
    @Test void scopesByBranchAndOpenedAtAndPreservesSnapshots() {
        Branch a=branchRepository.save(branch("Shift A")), b=branchRepository.save(branch("Shift B")); User ua=user(a), ub=user(b);
        LocalDateTime from=LocalDateTime.of(2026,7,1,0,0), to=from.plusDays(3), same=from.plusDays(2);
        WorkShift closed=shift(a,ua,ShiftStatus.CLOSED,from,  "500000","1500000","1480000","-20000","1200000","1000000","200000","0","50000",20);
        WorkShift open=shift(a,ua,ShiftStatus.OPEN,from.plusDays(1), "0","0",null,"0","0","0","0","0","0",0);
        WorkShift cancelled=shift(a,ua,ShiftStatus.CANCELLED,same, "0","0",null,"0","0","0","0","0","0",0);
        WorkShift sameTime=shift(a,ua,ShiftStatus.OPEN,same, "0","0",null,"0","0","0","0","0","0",0);
        shift(a,ua,ShiftStatus.OPEN,to,"0","0",null,"0","0","0","0","0","0",0); shift(a,ua,ShiftStatus.OPEN,from.minusSeconds(1),"0","0",null,"0","0","0","0","0","0",0); shift(b,ub,ShiftStatus.CLOSED,from.plusHours(1),"0","0","0","0","0","0","0","0","0",0);
        List<WorkShift> results=workShiftRepository.findByBranchIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDescIdDesc(a.getId(),from,to);
        assertEquals(4,results.size()); assertTrue(results.stream().allMatch(s->s.getBranch().getId().equals(a.getId()))); assertEquals(sameTime.getId(),results.get(0).getId()); assertEquals(cancelled.getId(),results.get(1).getId()); assertEquals(ShiftStatus.OPEN,open.getStatus()); assertEquals(ShiftStatus.CLOSED,closed.getStatus()); assertEquals(ShiftStatus.CANCELLED,cancelled.getStatus()); assertEquals(new BigDecimal("-20000"),closed.getCashDifference()); assertEquals(new BigDecimal("1200000"),closed.getTotalSales());
    }
    private Branch branch(String n){Branch b=new Branch();b.setName(n);b.setAddress(n);b.setPhone("0");b.setStatus("ACTIVE");return b;}
    private User user(Branch b){Role r=roleRepository.findByName("ROLE_ADMIN").orElseGet(()->roleRepository.save(new Role(null,"ROLE_ADMIN")));User u=new User();u.setUsername("shift-report-"+b.getName());u.setPassword("p");u.setRole(r);u.setBranch(b);u.setEnabled(true);return userRepository.save(u);}
    private WorkShift shift(Branch b,User u,ShiftStatus status,LocalDateTime opened,String opening,String expected,String actual,String difference,String total,String cash,String transfer,String card,String refund,int orders){WorkShift s=new WorkShift();s.setBranch(b);s.setCashier(u);s.setOpenedBy(u);s.setStatus(status);s.setOpenedAt(opened);s.setClosedAt(status==ShiftStatus.CLOSED?opened.plusHours(8):null);s.setOpeningCash(new BigDecimal(opening));s.setExpectedCash(new BigDecimal(expected));s.setActualCash(actual==null?null:new BigDecimal(actual));s.setCashDifference(new BigDecimal(difference));s.setTotalSales(new BigDecimal(total));s.setCashSales(new BigDecimal(cash));s.setTransferSales(new BigDecimal(transfer));s.setCardSales(new BigDecimal(card));s.setRefundTotal(new BigDecimal(refund));s.setOrderCount(orders);return workShiftRepository.save(s);}
}
