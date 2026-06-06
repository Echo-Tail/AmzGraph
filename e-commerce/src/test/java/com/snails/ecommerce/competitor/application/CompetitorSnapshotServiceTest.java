package com.snails.ecommerce.competitor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.api.ManualCompetitorSnapshotRequest;
import com.snails.ecommerce.competitor.api.SubmitManualCompetitorsRequest;
import com.snails.ecommerce.competitor.domain.CompetitorSnapshot;
import com.snails.ecommerce.competitor.domain.CompetitorSourceType;
import com.snails.ecommerce.competitor.infrastructure.CompetitorSnapshotRepository;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 竞品快照应用服务测试。
 *
 * <p>使用真实 H2 仓储验证手工补录的任务状态、ASIN 范围和批量原子性规则。</p>
 */
@SpringBootTest
class CompetitorSnapshotServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private CompetitorSnapshotRepository competitorSnapshotRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private CompetitorSnapshotService service;

    @BeforeEach
    void setUp() {
        competitorSnapshotRepository.deleteAll();
        listingTaskRepository.deleteAll();
        service = new CompetitorSnapshotService(
                listingTaskRepository,
                competitorSnapshotRepository,
                new IdGenerator(),
                objectMapper);
    }

    @Test
    void submitsManualSnapshotsForTaskAsins() {
        ListingTask task = saveTask("task_submit", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        SubmitManualCompetitorsRequest request = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(
                        snapshotRequest("b0first", "First Car Stereo"),
                        snapshotRequest(" B0SECOND ", "Second Car Stereo")));

        List<CompetitorSnapshotResponse> created =
                service.submitManualSnapshots(task.getTaskId(), request);

        assertThat(created).hasSize(2);
        assertThat(created)
                .extracting(CompetitorSnapshotResponse::asin)
                .containsExactly("B0FIRST", "B0SECOND");
        assertThat(created)
                .extracting(CompetitorSnapshotResponse::sourceType)
                .containsOnly("MANUAL");
        assertThat(created)
                .extracting(CompetitorSnapshotResponse::createdBy)
                .containsOnly("operator@example.com");
        assertThat(created)
                .extracting(CompetitorSnapshotResponse::sourceName)
                .containsOnly("Manual Entry");
    }

    @Test
    void createsNewSnapshotForRepeatedAsin() {
        ListingTask task = saveTask("task_repeat", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        SubmitManualCompetitorsRequest first = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(snapshotRequest("B0FIRST", "First Version")));
        SubmitManualCompetitorsRequest second = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(snapshotRequest("B0FIRST", "Second Version")));

        List<CompetitorSnapshotResponse> firstCreated =
                service.submitManualSnapshots(task.getTaskId(), first);
        List<CompetitorSnapshotResponse> secondCreated =
                service.submitManualSnapshots(task.getTaskId(), second);

        assertThat(competitorSnapshotRepository.count()).isEqualTo(2);
        assertThat(firstCreated.get(0).snapshotId()).isNotEqualTo(secondCreated.get(0).snapshotId());
    }

    @Test
    void rejectsAsinOutsideTaskInput() {
        ListingTask task = saveTask("task_outside", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        SubmitManualCompetitorsRequest request = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(snapshotRequest("B0UNKNOWN", "Unknown Product")));

        assertThatThrownBy(() -> service.submitManualSnapshots(task.getTaskId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsDuplicateAsinInOneRequest() {
        ListingTask task = saveTask("task_duplicate", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        SubmitManualCompetitorsRequest request = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(
                        snapshotRequest("B0FIRST", "First Product"),
                        snapshotRequest(" b0first ", "Duplicate Product")));

        assertThatThrownBy(() -> service.submitManualSnapshots(task.getTaskId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsSubmissionWhenTaskIsNotWaitingForBriefApproval() {
        ListingTask task = saveTask("task_generating", ListingTaskStatus.GENERATING);
        SubmitManualCompetitorsRequest request = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(snapshotRequest("B0FIRST", "First Product")));

        assertThatThrownBy(() -> service.submitManualSnapshots(task.getTaskId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsWholeBatchWhenOneSnapshotIsInvalid() {
        ListingTask task = saveTask("task_atomic", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        SubmitManualCompetitorsRequest request = new SubmitManualCompetitorsRequest(
                "operator@example.com",
                List.of(
                        snapshotRequest("B0FIRST", "Valid Product"),
                        snapshotRequest("B0UNKNOWN", "Invalid Product")));

        assertThatThrownBy(() -> service.submitManualSnapshots(task.getTaskId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(competitorSnapshotRepository.count()).isZero();
    }

    @Test
    void listsAllSnapshotsNewestFirst() {
        ListingTask task = saveTask("task_history", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        saveSnapshot("competitor_001", task.getTaskId(), "B0FIRST", "First Version",
                LocalDateTime.of(2026, 6, 6, 10, 0));
        saveSnapshot("competitor_002", task.getTaskId(), "B0FIRST", "Second Version",
                LocalDateTime.of(2026, 6, 6, 11, 0));

        List<CompetitorSnapshotResponse> snapshots = service.listSnapshots(task.getTaskId());

        assertThat(snapshots)
                .extracting(CompetitorSnapshotResponse::snapshotId)
                .containsExactly("competitor_002", "competitor_001");
    }

    @Test
    void listsLatestSnapshotPerAsinInTaskInputOrder() {
        ListingTask task = saveTask("task_latest", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        saveSnapshot("competitor_first_old", task.getTaskId(), "B0FIRST", "First Old",
                LocalDateTime.of(2026, 6, 6, 9, 0));
        saveSnapshot("competitor_second", task.getTaskId(), "B0SECOND", "Second Latest",
                LocalDateTime.of(2026, 6, 6, 11, 0));
        saveSnapshot("competitor_first_new", task.getTaskId(), "B0FIRST", "First Latest",
                LocalDateTime.of(2026, 6, 6, 12, 0));

        List<CompetitorSnapshotResponse> latest = service.listLatestSnapshots(task.getTaskId());

        assertThat(latest)
                .extracting(CompetitorSnapshotResponse::asin)
                .containsExactly("B0FIRST", "B0SECOND");
        assertThat(latest)
                .extracting(CompetitorSnapshotResponse::title)
                .containsExactly("First Latest", "Second Latest");
    }

    @Test
    void omitsTaskAsinWithoutSnapshotFromLatestView() {
        ListingTask task = saveTask("task_partial_latest", ListingTaskStatus.WAIT_BRIEF_APPROVE);
        saveSnapshot("competitor_first", task.getTaskId(), "B0FIRST", "First Latest",
                LocalDateTime.of(2026, 6, 6, 12, 0));

        List<CompetitorSnapshotResponse> latest = service.listLatestSnapshots(task.getTaskId());

        assertThat(latest)
                .extracting(CompetitorSnapshotResponse::asin)
                .containsExactly("B0FIRST");
    }

    @Test
    void allowsQueriesAfterTaskMovesToGenerating() {
        ListingTask task = saveTask("task_query_generating", ListingTaskStatus.GENERATING);
        saveSnapshot("competitor_generating", task.getTaskId(), "B0FIRST", "Existing Snapshot",
                LocalDateTime.of(2026, 6, 6, 12, 0));

        assertThat(service.listSnapshots(task.getTaskId())).hasSize(1);
        assertThat(service.listLatestSnapshots(task.getTaskId())).hasSize(1);
    }

    @Test
    void rejectsQueriesForMissingTask() {
        assertThatThrownBy(() -> service.listSnapshots("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
        assertThatThrownBy(() -> service.listLatestSnapshots("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    private ListingTask saveTask(String taskId, ListingTaskStatus status) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setTextStatus(GenerationStatus.NOT_STARTED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(status == ListingTaskStatus.WAIT_BRIEF_APPROVE
                ? BriefStatus.WAIT_APPROVE
                : BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[]");
        task.setCompetitorAsinsJson("[\"B0FIRST\",\"B0SECOND\"]");
        return listingTaskRepository.save(task);
    }

    private ManualCompetitorSnapshotRequest snapshotRequest(String asin, String title) {
        return new ManualCompetitorSnapshotRequest(
                asin,
                title,
                List.of("Wireless CarPlay", "Android Auto"),
                new BigDecimal("4.4"),
                325L,
                List.of("Installation instructions are unclear"),
                List.of("wireless carplay stereo"),
                null);
    }

    private CompetitorSnapshot saveSnapshot(
            String snapshotId,
            String taskId,
            String asin,
            String title,
            LocalDateTime capturedAt) {
        CompetitorSnapshot snapshot = new CompetitorSnapshot();
        snapshot.setSnapshotId(snapshotId);
        snapshot.setTaskId(taskId);
        snapshot.setAsin(asin);
        snapshot.setTitle(title);
        snapshot.setBulletPointsJson("[]");
        snapshot.setReviewPainPointsJson("[]");
        snapshot.setKeywordSignalsJson("[]");
        snapshot.setSourceType(CompetitorSourceType.MANUAL);
        snapshot.setSourceName("Manual Entry");
        snapshot.setCapturedAt(capturedAt);
        snapshot.setCreatedBy("operator@example.com");
        return competitorSnapshotRepository.save(snapshot);
    }
}
