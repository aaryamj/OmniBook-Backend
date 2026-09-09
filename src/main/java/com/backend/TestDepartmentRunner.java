package com.backend;

import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import com.backend.repository.ProviderProfileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestDepartmentRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ProviderProfileRepository providerProfileRepository;

    public TestDepartmentRunner(UserRepository userRepository, TenantRepository tenantRepository, ProviderProfileRepository providerProfileRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.providerProfileRepository = providerProfileRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("====== FIXING ORPHAN USERS & SYNCING PROFILE PICTURES ======");
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            if (tenants.isEmpty()) {
                System.out.println("No tenants exist in the database!");
                return;
            }
            Tenant defaultTenant = tenants.get(0);
            
            List<User> users = userRepository.findAll();
            int fixedCount = 0;
            int picSyncedCount = 0;
            for (User u : users) {
                if (("admin".equals(u.getRole()) || "service_provider".equals(u.getRole()) || "provider".equals(u.getRole())) 
                    && u.getTenant() == null) {
                    u.setTenant(defaultTenant);
                    fixedCount++;
                    System.out.println("Assigned tenant to: " + u.getEmail());
                }

                // Sync profile picture from provider profile if missing
                if ((u.getProfilePicture() == null || u.getProfilePicture().trim().isEmpty()) 
                    && ("service_provider".equals(u.getRole()) || "provider".equals(u.getRole()))) {
                    var prof = providerProfileRepository.findByUser(u).orElse(null);
                    if (prof != null && prof.getProfilePictureUrl() != null && !prof.getProfilePictureUrl().trim().isEmpty()) {
                        u.setProfilePicture(prof.getProfilePictureUrl());
                        picSyncedCount++;
                    }
                }
                userRepository.save(u);
            }
            System.out.println("Fixed " + fixedCount + " orphan users. Synced " + picSyncedCount + " provider profile pictures.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("============================================================");
    }
}
