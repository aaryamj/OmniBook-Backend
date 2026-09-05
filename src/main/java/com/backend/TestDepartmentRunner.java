package com.backend;

import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestDepartmentRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public TestDepartmentRunner(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("====== FIXING ORPHAN USERS ======");
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            if (tenants.isEmpty()) {
                System.out.println("No tenants exist in the database!");
                return;
            }
            Tenant defaultTenant = tenants.get(0);
            
            List<User> users = userRepository.findAll();
            int fixedCount = 0;
            for (User u : users) {
                if (("admin".equals(u.getRole()) || "service_provider".equals(u.getRole()) || "provider".equals(u.getRole())) 
                    && u.getTenant() == null) {
                    u.setTenant(defaultTenant);
                    userRepository.save(u);
                    fixedCount++;
                    System.out.println("Assigned tenant to: " + u.getEmail());
                }
            }
            System.out.println("Fixed " + fixedCount + " orphan users.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=================================");
    }
}
