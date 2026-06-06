package com.snails.ecommerce.listing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建 Brief 人工修改版本的请求。
 *
 * <p>基础版本必须是任务当前最新版本；列表允许为空，但不能传入 {@code null} 或空白元素。</p>
 *
 * @param baseBriefVersionId 修改所基于的最新 Brief 版本 ID
 * @param createdBy 创建该人工版本的操作人
 * @param targetAudience 目标受众
 * @param coreSellingPoints 核心卖点
 * @param targetKeywords 目标关键词
 * @param forbiddenClaims 禁用声明
 * @param imageDirectionPrompts 图片方向提示
 * @param complianceNotes 合规提示
 */
public record CreateBriefVersionRequest(
        @NotBlank String baseBriefVersionId,
        @NotBlank String createdBy,
        @NotBlank String targetAudience,
        @NotNull List<@NotBlank String> coreSellingPoints,
        @NotNull List<@NotBlank String> targetKeywords,
        @NotNull List<@NotBlank String> forbiddenClaims,
        @NotNull List<@NotBlank String> imageDirectionPrompts,
        @NotNull List<@NotBlank String> complianceNotes
) {
}
