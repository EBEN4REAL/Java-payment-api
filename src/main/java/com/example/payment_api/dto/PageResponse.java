package com.example.payment_api.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

// Our own page shape, so the JSON contract doesn't depend on Spring Data's Page internals.
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
