package com.snails.ecommerce.competitor.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.api.ManualCompetitorSnapshotRequest;
import com.snails.ecommerce.competitor.api.SubmitManualCompetitorsRequest;
import com.snails.ecommerce.competitor.domain.CompetitorSnapshot;
import com.snails.ecommerce.competitor.domain.CompetitorSourceType;
import com.snails.ecommerce.competitor.infrastructure.CompetitorSnapshotRepository;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 竞品快照应用服务。
 *
 * <p>负责校验任务状态和原始 ASIN 范围，并以追加写入方式保存手工竞品快照。
 * 当前阶段不修改任务输入、不创建 Brief，也不调用外部竞品供应商。</p>
 */
@Service
@RequiredArgsConstructor
public class CompetitorSnapshotService {

    /** Listing 任务仓储，用于读取任务状态和原始 ASIN。 */
    private final ListingTaskRepository listingTaskRepository;

    /** 竞品快照仓储。 */
    private final CompetitorSnapshotRepository competitorSnapshotRepository;

    /** 快照业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** 任务 ASIN 和快照列表字段的 JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /**
     * 批量保存手工竞品快照。
     *
     * <p>服务会在写入任何记录前完成整批校验，保证一条数据无效时不产生部分快照。</p>
     *
     * @param taskId Listing 任务 ID
     * @param request 手工竞品批量请求
     * @return 本次创建的快照
     */
    @Transactional
    public List<CompetitorSnapshotResponse> submitManualSnapshots(
            String taskId,
            SubmitManualCompetitorsRequest request) {
        ListingTask task = requireTask(taskId);
        requireManualSubmissionAllowed(task);

        Set<String> taskAsins = new HashSet<>(readTaskAsins(task));
        List<NormalizedManualSnapshot> normalizedSnapshots = validateAndNormalizeBatch(request, taskAsins);
        LocalDateTime capturedAt = LocalDateTime.now();

        List<CompetitorSnapshot> entities = normalizedSnapshots.stream()
                .map(snapshot -> createManualEntity(taskId, request.createdBy(), snapshot, capturedAt))
                .toList();

        return competitorSnapshotRepository.saveAll(entities).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询任务的全部竞品快照历史。
     *
     * <p>结果按采集时间倒序返回；采集时间相同时按快照 ID 倒序，保证顺序稳定。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 任务的全部竞品快照
     */
    @Transactional(readOnly = true)
    public List<CompetitorSnapshotResponse> listSnapshots(String taskId) {
        requireTask(taskId);
        return competitorSnapshotRepository
                .findByTaskIdOrderByCapturedAtDescSnapshotIdDesc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询任务中每个 ASIN 的最新竞品快照。
     *
     * <p>输出顺序沿用任务提交时的 ASIN 顺序；尚无快照的 ASIN 不返回。该查询不受任务当前状态限制，
     * 便于后续生成和审计阶段复用已经固化的竞品数据。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 各 ASIN 的最新竞品快照
     */
    @Transactional(readOnly = true)
    public List<CompetitorSnapshotResponse> listLatestSnapshots(String taskId) {
        ListingTask task = requireTask(taskId);
        Map<String, CompetitorSnapshot> latestByAsin = new LinkedHashMap<>();
        competitorSnapshotRepository
                .findByTaskIdOrderByCapturedAtDescSnapshotIdDesc(taskId)
                .forEach(snapshot -> latestByAsin.putIfAbsent(
                        normalizeAsin(snapshot.getAsin()),
                        snapshot));

        return readTaskAsins(task).stream()
                .map(latestByAsin::get)
                .filter(snapshot -> snapshot != null)
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询任务，不存在时返回稳定业务错误。
     */
    private ListingTask requireTask(String taskId) {
        return listingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TASK_NOT_FOUND,
                        "Listing task not found: " + taskId));
    }

    /**
     * 校验任务仍允许补充 Brief 输入数据。
     */
    private void requireManualSubmissionAllowed(ListingTask task) {
        if (task.getStatus() != ListingTaskStatus.WAIT_BRIEF_APPROVE) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task does not allow competitor input: " + task.getTaskId());
        }
    }

    /**
     * 解析并标准化任务提交时保存的 ASIN 输入。
     */
    private List<String> readTaskAsins(ListingTask task) {
        try {
            List<String> asins = objectMapper.readValue(
                    task.getCompetitorAsinsJson() == null ? "[]" : task.getCompetitorAsinsJson(),
                    new TypeReference<List<String>>() {
                    });
            return asins.stream()
                    .filter(StringUtils::hasText)
                    .map(this::normalizeAsin)
                    .toList();
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process competitor JSON fields");
        }
    }

    /**
     * 在持久化前完成整批 ASIN 去重和输入范围校验。
     */
    private List<NormalizedManualSnapshot> validateAndNormalizeBatch(
            SubmitManualCompetitorsRequest request,
            Set<String> taskAsins) {
        Set<String> requestAsins = new HashSet<>();
        List<NormalizedManualSnapshot> normalized = new ArrayList<>();

        for (ManualCompetitorSnapshotRequest snapshot : request.snapshots()) {
            String asin = normalizeAsin(snapshot.asin());
            if (!requestAsins.add(asin)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate ASIN in request: " + asin);
            }
            if (!taskAsins.contains(asin)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "ASIN does not belong to task input: " + asin);
            }
            normalized.add(new NormalizedManualSnapshot(asin, snapshot));
        }
        return normalized;
    }

    /**
     * 创建单条手工快照实体。
     */
    private CompetitorSnapshot createManualEntity(
            String taskId,
            String createdBy,
            NormalizedManualSnapshot normalized,
            LocalDateTime capturedAt) {
        ManualCompetitorSnapshotRequest request = normalized.request();
        CompetitorSnapshot snapshot = new CompetitorSnapshot();
        snapshot.setSnapshotId(idGenerator.generate("competitor"));
        snapshot.setTaskId(taskId);
        snapshot.setAsin(normalized.asin());
        snapshot.setTitle(request.title().trim());
        snapshot.setBulletPointsJson(writeStringList(request.bulletPoints()));
        snapshot.setRating(request.rating());
        snapshot.setReviewCount(request.reviewCount());
        snapshot.setReviewPainPointsJson(writeStringList(request.reviewPainPoints()));
        snapshot.setKeywordSignalsJson(writeStringList(request.keywordSignals()));
        snapshot.setSourceType(CompetitorSourceType.MANUAL);
        snapshot.setSourceName(StringUtils.hasText(request.sourceName())
                ? request.sourceName().trim()
                : "Manual Entry");
        snapshot.setRawResponseFileKey(null);
        snapshot.setCapturedAt(capturedAt);
        snapshot.setCreatedBy(createdBy.trim());
        return snapshot;
    }

    /**
     * 将竞品快照实体转换为 API 响应。
     */
    private CompetitorSnapshotResponse toResponse(CompetitorSnapshot snapshot) {
        return new CompetitorSnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getTaskId(),
                snapshot.getAsin(),
                snapshot.getTitle(),
                readStringList(snapshot.getBulletPointsJson()),
                snapshot.getRating(),
                snapshot.getReviewCount(),
                readStringList(snapshot.getReviewPainPointsJson()),
                readStringList(snapshot.getKeywordSignalsJson()),
                snapshot.getSourceType().name(),
                snapshot.getSourceName(),
                snapshot.getRawResponseFileKey(),
                snapshot.getCapturedAt(),
                snapshot.getCreatedBy(),
                snapshot.getCreatedAt());
    }

    /**
     * 标准化 ASIN，避免大小写和首尾空格造成范围判断不一致。
     */
    private String normalizeAsin(String asin) {
        return asin.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 序列化快照列表字段。
     */
    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process competitor JSON fields");
        }
    }

    /**
     * 解析快照列表字段。
     */
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    json == null ? "[]" : json,
                    new TypeReference<List<String>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process competitor JSON fields");
        }
    }

    /**
     * 保存批次预校验后的 ASIN 和原始请求。
     */
    private record NormalizedManualSnapshot(
            String asin,
            ManualCompetitorSnapshotRequest request
    ) {
    }
}
