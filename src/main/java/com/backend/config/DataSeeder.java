package com.backend.config;

import com.backend.model.*;
import com.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final TenantRepository tenantRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;

    public DataSeeder(UserRepository userRepository, 
                      PasswordEncoder passwordEncoder,
                      TenantRepository tenantRepository,
                      ProviderProfileRepository providerProfileRepository,
                      ProviderServiceRepository providerServiceRepository,
                      ProviderScheduleRepository providerScheduleRepository,
                      TenantScheduleRepository tenantScheduleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerServiceRepository = providerServiceRepository;
        this.providerScheduleRepository = providerScheduleRepository;
        this.tenantScheduleRepository = tenantScheduleRepository;
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

        User patientUser = userRepository.findByEmail("patient@example.com").orElseGet(() -> {
            User u = User.builder()
                    .fullName("Patient User")
                    .email("patient@example.com")
                    .role("user")
                    .enabled(true)
                    .authProvider(AuthProvider.LOCAL)
                    .password(passwordEncoder.encode("User@1234"))
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepository.save(u);
        });
        patientUser.setPassword(passwordEncoder.encode("User@1234"));
        patientUser.setEnabled(true);
        userRepository.save(patientUser);


        // Seed Omega College (Tenant 29) provider, services, and schedules
        tenantRepository.findById(29L).ifPresent(omega -> {
            String providerEmail = "ramesh.karki@omegacollege.edu";
            User provider = userRepository.findByEmail(providerEmail).orElseGet(() -> {
                User u = User.builder()
                        .fullName("Prof. Dr. Ramesh Karki")
                        .email(providerEmail)
                        .role("service_provider")
                        .tenant(omega)
                        .enabled(true)
                        .authProvider(AuthProvider.LOCAL)
                        .password(passwordEncoder.encode("Provider@1234"))
                        .createdAt(LocalDateTime.now())
                        .build();
                return userRepository.save(u);
            });

            ProviderProfile profile = providerProfileRepository.findByUser(provider).orElseGet(() -> {
                ProviderProfile pp = new ProviderProfile();
                pp.setUser(provider);
                pp.setCredentials("Ph.D. in Computer Science");
                pp.setPrimarySpecialty("Academic Advisor & Faculty");
                pp.setStatus(com.backend.model.ProviderStatus.ACTIVE);
                pp.setTier("Professional");
                pp.setCommissionRate(0.0);
                return providerProfileRepository.save(pp);
            });

            if (providerServiceRepository.findByProviderProfile(profile).isEmpty()) {
                ProviderService s1 = new ProviderService();
                s1.setProviderProfile(profile);
                s1.setServiceName("Academic Advising");
                s1.setDurationMinutes(30);
                s1.setFee(500.0);
                s1.setIsTelemedicine(false);
                s1.setCategory("Education");
                s1.setIsActive(true);
                s1.setMaxCapacity(1);

                ProviderService s2 = new ProviderService();
                s2.setProviderProfile(profile);
                s2.setServiceName("Faculty Consultation");
                s2.setDurationMinutes(45);
                s2.setFee(750.0);
                s2.setIsTelemedicine(false);
                s2.setCategory("Education");
                s2.setIsActive(true);
                s2.setMaxCapacity(1);

                ProviderService s3 = new ProviderService();
                s3.setProviderProfile(profile);
                s3.setServiceName("Career Counseling");
                s3.setDurationMinutes(30);
                s3.setFee(400.0);
                s3.setIsTelemedicine(false);
                s3.setCategory("Education");
                s3.setIsActive(true);
                s3.setMaxCapacity(1);

                providerServiceRepository.saveAll(java.util.List.of(s1, s2, s3));
                System.out.println("✅ Seeded services for Omega College (Academic Advising, Faculty Consultation, Career Counseling)");
            }

            // Seed tenant & provider schedules (Sunday - Friday 09:00 - 17:00)
            String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            for (String day : days) {
                boolean isWorkDay = !"Saturday".equalsIgnoreCase(day);
                if (tenantScheduleRepository.findByTenantIdAndDayOfWeek(omega.getId(), day).isEmpty()) {
                    TenantSchedule ts = TenantSchedule.builder()
                            .tenant(omega)
                            .dayOfWeek(day)
                            .isActive(isWorkDay)
                            .openingTime(isWorkDay ? java.time.LocalTime.of(9, 0) : null)
                            .closingTime(isWorkDay ? java.time.LocalTime.of(17, 0) : null)
                            .breakStartTime(isWorkDay ? java.time.LocalTime.of(12, 30) : null)
                            .breakEndTime(isWorkDay ? java.time.LocalTime.of(13, 30) : null)
                            .closedMessage(!isWorkDay ? "Weekend" : null)
                            .build();
                    tenantScheduleRepository.save(ts);
                }

                if (providerScheduleRepository.findByProviderAndDayOfWeek(provider, day).isEmpty()) {
                    ProviderSchedule ps = ProviderSchedule.builder()
                            .provider(provider)
                            .dayOfWeek(day)
                            .isActive(isWorkDay)
                            .openingTime(isWorkDay ? java.time.LocalTime.of(9, 0) : null)
                            .closingTime(isWorkDay ? java.time.LocalTime.of(17, 0) : null)
                            .breakStartTime(isWorkDay ? java.time.LocalTime.of(12, 30) : null)
                            .breakEndTime(isWorkDay ? java.time.LocalTime.of(13, 30) : null)
                            .closedMessage(!isWorkDay ? "Closed for weekend" : null)
                            .build();
                    providerScheduleRepository.save(ps);
                }
            }
            System.out.println("✅ Seeded schedules for Omega College & Prof. Dr. Ramesh Karki");
        });
    }
}
