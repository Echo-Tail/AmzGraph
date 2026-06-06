package com.snails.ecommerce.listing.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 批准 Brief 版本的请求。
 *
 * @param approvedBy 执行批准操作的人员标识
 */
public record ApproveBriefRequest(@NotBlank String approvedBy) {
}
