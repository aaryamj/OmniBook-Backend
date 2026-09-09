package com.backend.config;

import com.backend.model.AuthProvider;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if any admin already exists to prevent duplicate creation
        if (!userRepository.existsByRole("super_admin")) {
            
            User superAdmin = User.builder()
                    .fullName("System Admin")
                    .email("superadmin@omnibook.com")
                    // The password will be hashed using your existing BCryptPasswordEncoder
                    .password(passwordEncoder.encode("Admin@1234")) 
                    .role("super_admin")
                    .enabled(true)
                    .authProvider(AuthProvider.LOCAL)
                    .createdAt(LocalDateTime.now())
                    .build();

            userRepository.save(superAdmin);
            
            System.out.println("=======================================================");
            System.out.println("✅ SuperAdmin account generated successfully!");
            System.out.println("Email: superadmin@omnibook.com");
            System.out.println("Password: Admin@1234");
            System.out.println("=======================================================");
        }

        userRepository.findByEmail("aakashsah620@gmail.com").ifPresent(u -> {
            u.setPassword(passwordEncoder.encode("Provider@1234"));
            userRepository.save(u);
            System.out.println("✅ Test provider password set for aakashsah620@gmail.com -> Provider@1234");
        });
    }
}
