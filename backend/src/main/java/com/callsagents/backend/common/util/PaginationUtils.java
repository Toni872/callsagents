package com.callsagents.backend.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Shared pagination & sort utilities.
 * Validates sort fields against a per-endpoint allowlist to prevent sort injection.
 */
public final class PaginationUtils {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private PaginationUtils() {}

    /**
     * Builds a safe Pageable with sort field validation.
     *
     * @param page           page index (0-based, clamped to >= 0)
     * @param size           page size (clamped to [1, MAX_PAGE_SIZE])
     * @param sort           raw sort string, e.g. "createdAt,desc"
     * @param defaultField   fallback sort field when sort is blank
     * @param defaultDir     fallback sort direction when sort is blank
     * @param allowedFields set of permitted field names for this entity
     * @return validated Pageable
     * @throws ResponseStatusException 400 if the field is not in the allowlist
     */
    public static Pageable buildPageable(int page, int size, String sort,
                                         String defaultField, Sort.Direction defaultDir,
                                         Set<String> allowedFields) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sortSpec = parseSort(sort, defaultField, defaultDir, allowedFields);
        return PageRequest.of(safePage, safeSize, sortSpec);
    }

    private static Sort parseSort(String raw, String defaultField, Sort.Direction defaultDir,
                                  Set<String> allowedFields) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(defaultDir, defaultField);
        }
        String[] parts = raw.split(",");
        String field = parts[0].trim();

        if (!allowedFields.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Sort field '" + field + "' is not allowed. Permitted: " + allowedFields);
        }

        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
            ? Sort.Direction.ASC
            : defaultDir;
        return Sort.by(direction, field);
    }
}
