package com.backend.service;

import com.backend.dto.ProviderProfileDTO;
import com.backend.model.ProviderProfile;
import com.backend.model.User;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.UserRepository;
import com.backend.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderProfileService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final FileStorageService fileStorageService;

    public ProviderProfileDTO getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!"service_provider".equals(user.getRole())) {
            throw new RuntimeException("User is not a service provider");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(user).orElse(null);

        return ProviderProfileDTO.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .specialization(profile != null && profile.getPrimarySpecialty() != null ? profile.getPrimarySpecialty() : user.getSpecialization())
                .licenseNumber(profile != null && profile.getMedicalLicense() != null ? profile.getMedicalLicense() : user.getLicenseNumber())
                .isScheduleDelegated(user.getIsScheduleDelegated() != null ? user.getIsScheduleDelegated() : true)
                .profilePictureUrl(profile != null ? profile.getProfilePictureUrl() : null)
                .build();
    }

    public ProviderProfileDTO updateProfile(String email, ProviderProfileDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!"service_provider".equals(user.getRole())) {
            throw new RuntimeException("User is not a service provider");
        }

        if (dto.getFullName() != null) user.setFullName(dto.getFullName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getIsScheduleDelegated() != null) user.setIsScheduleDelegated(dto.getIsScheduleDelegated());

        userRepository.save(user);

        ProviderProfile profile = providerProfileRepository.findByUser(user)
                .orElseGet(() -> {
                    ProviderProfile newProfile = new ProviderProfile();
                    newProfile.setUser(user);
                    return newProfile;
                });
        
        boolean profileUpdated = false;
        if (dto.getSpecialization() != null) {
            profile.setPrimarySpecialty(dto.getSpecialization());
            user.setSpecialization(dto.getSpecialization()); // Keep in sync for now
            profileUpdated = true;
        }
        if (dto.getLicenseNumber() != null) {
            profile.setMedicalLicense(dto.getLicenseNumber());
            user.setLicenseNumber(dto.getLicenseNumber()); // Keep in sync for now
            profileUpdated = true;
        }
        
        if (profileUpdated) {
            providerProfileRepository.save(profile);
            userRepository.save(user);
        }

        return getProfile(email);
    }

    public ProviderProfileDTO uploadProfilePicture(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        ProviderProfile profile = providerProfileRepository.findByUser(user)
                .orElseGet(() -> {
                    ProviderProfile newProfile = new ProviderProfile();
                    newProfile.setUser(user);
                    return newProfile;
                });

        if (file != null && !file.isEmpty()) {
            String fileName = fileStorageService.storeFile(file);
            String logoUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(fileName)
                    .toUriString();
            profile.setProfilePictureUrl(logoUrl);
            providerProfileRepository.save(profile);
        }

        return getProfile(email);
    }
}
