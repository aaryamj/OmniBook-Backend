package com.backend.service;

import com.backend.dto.DepartmentDTO;
import com.backend.dto.DepartmentRequest;
import com.backend.model.Department;
import com.backend.model.User;
import com.backend.repository.DepartmentRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final com.backend.repository.ProviderProfileRepository providerProfileRepository;

    private User getAdminUser(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin has no tenant associated");
        }
        return admin;
    }

    public List<DepartmentDTO> getAllDepartments(String adminEmail) {
        User admin = getAdminUser(adminEmail);
        return departmentRepository.findByTenantId(admin.getTenant().getId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public DepartmentDTO createDepartment(String adminEmail, DepartmentRequest request) {
        User admin = getAdminUser(adminEmail);
        
        Department department = Department.builder()
                .name(request.getName())
                .code(request.getCode())
                .headName(request.getHeadName())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .tenant(admin.getTenant())
                .build();
                
        department = departmentRepository.save(department);
        log.info("Admin {} created department {}", adminEmail, department.getName());
        return mapToDTO(department);
    }

    public DepartmentDTO updateDepartment(String adminEmail, Long id, DepartmentRequest request) {
        User admin = getAdminUser(adminEmail);
        
        Department department = departmentRepository.findByIdAndTenantId(id, admin.getTenant().getId())
                .orElseThrow(() -> new RuntimeException("Department not found"));
                
        if (request.getName() != null) department.setName(request.getName());
        if (request.getCode() != null) department.setCode(request.getCode());
        if (request.getHeadName() != null) department.setHeadName(request.getHeadName());
        if (request.getDescription() != null) department.setDescription(request.getDescription());
        if (request.getIsActive() != null) department.setActive(request.getIsActive());
        
        department = departmentRepository.save(department);
        log.info("Admin {} updated department {}", adminEmail, department.getName());
        return mapToDTO(department);
    }

    public void deleteDepartment(String adminEmail, Long id) {
        User admin = getAdminUser(adminEmail);
        
        Department department = departmentRepository.findByIdAndTenantId(id, admin.getTenant().getId())
                .orElseThrow(() -> new RuntimeException("Department not found"));
                
        departmentRepository.delete(department);
        log.info("Admin {} deleted department {}", adminEmail, department.getName());
    }

    private DepartmentDTO mapToDTO(Department department) {
        Long tenantId = department.getTenant() != null ? department.getTenant().getId() : null;
        List<String> approved = new ArrayList<>();
        if (tenantId != null) {
            approved = providerProfileRepository.findByTenantId(tenantId).stream()
                    .filter(p -> p.getStatus() == com.backend.model.ProviderStatus.ACTIVE)
                    .filter(p -> p.getPrimarySpecialty() != null &&
                            (p.getPrimarySpecialty().trim().equalsIgnoreCase(department.getName().trim()) ||
                             (department.getCode() != null && !department.getCode().isBlank() && p.getPrimarySpecialty().trim().equalsIgnoreCase(department.getCode().trim()))))
                    .map(p -> p.getUser() != null ? p.getUser().getFullName() : null)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }

        return DepartmentDTO.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .headName(department.getHeadName())
                .description(department.getDescription())
                .isActive(department.isActive())
                .approvedProviders(approved)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }
}
