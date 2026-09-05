package com.backend.controller;

import com.backend.dto.DepartmentDTO;
import com.backend.dto.DepartmentRequest;
import com.backend.service.AdminDepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/departments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    private String getAdminEmail() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getUsername();
    }

    @GetMapping
    public ResponseEntity<List<DepartmentDTO>> getAllDepartments() {
        try {
            return ResponseEntity.ok(adminDepartmentService.getAllDepartments(getAdminEmail()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<DepartmentDTO> createDepartment(@RequestBody DepartmentRequest request) {
        try {
            return ResponseEntity.ok(adminDepartmentService.createDepartment(getAdminEmail(), request));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDTO> updateDepartment(@PathVariable Long id, @RequestBody DepartmentRequest request) {
        try {
            return ResponseEntity.ok(adminDepartmentService.updateDepartment(getAdminEmail(), id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        try {
            adminDepartmentService.deleteDepartment(getAdminEmail(), id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
