package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import java.util.List;

/**
 * Listing 任务详情响应。
 *
 * <p>该响应用于任务详情页展示任务主状态、子流程状态、模板信息、输入文件和最新 Brief 摘要。</p>
 *
 * @param taskId 任务 ID
 * @param status 任务主状态
 * @param textStatus 文案生成状态
 * @param imageStatus 图片生成状态
 * @param briefStatus Brief 审核状态
 * @param categoryCode 类目代码
 * @param categoryTemplateId 类目模板 ID
 * @param marketplace 站点市场
 * @param language 生成语言
 * @param originalProductUrls 原始产品图文件键列表
 * @param competitorAsins 竞品 ASIN 列表
 * @param latestBrief 最新 Brief 摘要
 */
public record ListingTaskDetailResponse(
        String taskId,
        String status,
        String textStatus,
        String imageStatus,
        String briefStatus,
        String categoryCode,
        String categoryTemplateId,
        String marketplace,
        String language,
        List<String> originalProductUrls,
        List<String> competitorAsins,
        BriefSummary latestBrief
) {

    /**
     * 从任务实体、最新 Brief 和已解析的列表字段创建 API 响应。
     *
     * <p>该方法只负责把内部模型映射为前端需要的响应形态，不把 JPA 实体直接暴露给 Controller。</p>
     */
    public static ListingTaskDetailResponse from(
            ListingTask task,
            ListingBriefVersion latestBrief,
            List<String> originalProductUrls,
            List<String> competitorAsins) {
        return new ListingTaskDetailResponse(
                task.getTaskId(),
                task.getStatus().name(),
                task.getTextStatus().name(),
                task.getImageStatus().name(),
                task.getBriefStatus().name(),
                task.getCategoryCode(),
                task.getCategoryTemplateId(),
                task.getMarketplace(),
                task.getLanguage(),
                originalProductUrls,
                competitorAsins,
                BriefSummary.from(latestBrief));
    }

    /**
     * Brief 摘要。
     *
     * @param briefVersionId Brief 版本 ID
     * @param targetAudience 目标受众
     * @param approved 是否已批准
     */
    public record BriefSummary(
            String briefVersionId,
            String targetAudience,
            boolean approved
    ) {

        /**
         * 从 Brief 版本实体创建摘要。没有 Brief 时返回 null，表示任务尚未生成 Brief。
         */
        public static BriefSummary from(ListingBriefVersion brief) {
            if (brief == null) {
                return null;
            }
            return new BriefSummary(
                    brief.getBriefVersionId(),
                    brief.getTargetAudience(),
                    brief.isApproved());
        }
    }
}
