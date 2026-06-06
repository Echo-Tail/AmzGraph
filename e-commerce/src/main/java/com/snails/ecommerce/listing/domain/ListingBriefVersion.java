package com.snails.ecommerce.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "listing_brief_version")
/**
 * Listing Brief 版本。
 *
 * <p>Brief 是生成文案和图片前的人工审核对象，运营批准后才进入后续生成阶段。</p>
 */
public class ListingBriefVersion {

    /** Brief 版本 ID。 */
    @Id
    @Column(length = 64)
    private String briefVersionId;

    /** 所属任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** 父 Brief 版本 ID，用于表达版本树。 */
    @Column(length = 64)
    private String parentBriefVersionId;

    /** 目标受众。 */
    @Column(length = 255)
    private String targetAudience;

    /** 核心卖点 JSON。 */
    @Column(columnDefinition = "text")
    private String coreSellingPointsJson;

    /** 目标关键词 JSON。 */
    @Column(columnDefinition = "text")
    private String targetKeywordsJson;

    /** 禁用声明 JSON。 */
    @Column(columnDefinition = "text")
    private String forbiddenClaimsJson;

    /** 图片方向 Prompt JSON。 */
    @Column(columnDefinition = "text")
    private String imageDirectionPromptsJson;

    /** 合规提示 JSON。 */
    @Column(columnDefinition = "text")
    private String complianceNotesJson;

    /** 是否已被运营批准。 */
    @Column(nullable = false)
    private boolean approved;

    /** Brief 版本创建人；系统生成版本固定为 SYSTEM。 */
    @Column(nullable = false, length = 128)
    private String createdBy;

    /** 批准该 Brief 版本的操作人，未批准时为空。 */
    @Column(length = 128)
    private String approvedBy;

    /** Brief 批准时间，未批准时为空。 */
    private LocalDateTime approvedAt;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建 Brief 版本时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
