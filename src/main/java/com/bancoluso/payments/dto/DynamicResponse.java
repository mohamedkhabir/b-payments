package com.bancoluso.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicResponse {
    private List<?> result;
    private int currentPage;
    private long totalItems;
    private int totalPages;
}
