package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Brief 版本完整响应。
 *
 * <p>该 DTO 用于审核接口稳定输出版本内容和审计信息，避免直接暴露 JPA 实体。</p>
 *
 * @param briefVersionId Brief 版本 ID
 * @param taskId 所属任务 ID
 * @param parentBriefVersionId 父 Brief 版本 ID
 * @param targetAudience 目标受众
 * @param coreSellingPoints 核心卖点
 * @param targetKeywords 目标关键词
 * @param forbiddenClaims 禁用声明
 * @param imageDirectionPrompts 图片方向提示
 * @param complianceNotes 合规提示
 * @param approved 是否已批准
 * @param createdBy 版本创建人
 * @param approvedBy 版本审批人
 * @param approvedAt 版本审批时间
 * @param createdAt 版本创建时间
 */
public record BriefVersionResponse(
        String briefVersionId,
        String taskId,
        String parentBriefVersionId,
        String targetAudience,
        List<String> coreSellingPoints,
        List<String> targetKeywords,
        List<String> forbiddenClaims,
        List<String> imageDirectionPrompts,
        List<String> complianceNotes,
        boolean approved,
        String createdBy,
        String approvedBy,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {
}
