package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResultDTO {
    private String id;
    private String category;
    private String title;
    private String subtitle;
    private String status;
    private String link;
    private String icon;
}
