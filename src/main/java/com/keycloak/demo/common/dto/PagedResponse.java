package com.keycloak.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Clean pagination wrapper that replaces Spring's default Page JSON output.
 *
 * WHY: Spring's Page<T> serializes into a deeply nested JSON with internal fields
 * like "pageable", "sort", "numberOfElements" that frontend developers don't need.
 * This DTO gives a flat, predictable structure:
 *
 * {
 *   "content": [...],        // The actual items for this page
 *   "pageNumber": 0,         // Current page (0-indexed)
 *   "pageSize": 20,          // Items per page
 *   "totalElements": 150,    // Total items across all pages
 *   "totalPages": 8,         // Total number of pages
 *   "last": false             // Whether this is the last page
 * }
 *
 * Usage: Wrap in ApiResponse -> ApiResponse.success(PagedResponse.from(page))
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    /**
     * Converts Spring's Page<T> into our clean PagedResponse<T>.
     * Called in controllers: PagedResponse.from(companyService.listUsers(pageable))
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
