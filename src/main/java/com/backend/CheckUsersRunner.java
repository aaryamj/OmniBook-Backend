package com.backend;

import com.backend.model.User;
import com.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CheckUsersRunner implements CommandLineRunner {
    private final UserRepository userRepository;

    public CheckUsersRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("====== USERS DUMP ======");
        for (User u : userRepository.findAll()) {
            System.out.println("ID: " + u.getId() + ", Email: " + u.getEmail() + ", Role: " + u.getRole() + ", Tenant: " + (u.getTenant() != null ? u.getTenant().getId() : "null"));
        }
        System.out.println("========================");
    }
}
