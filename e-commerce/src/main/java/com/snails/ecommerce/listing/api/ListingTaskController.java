package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.common.api.ApiResponse;
import com.snails.ecommerce.listing.application.ListingWorkflowService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Listing 任务接口。
 *
 * <p>第一阶段只提供任务提交接口，用于接收运营上传的产品资料、产品图和竞品 ASIN。</p>
 */
@RestController
@RequestMapping("/api/v1/listing")
@RequiredArgsConstructor
public class ListingTaskController {

    /** Listing 工作流应用服务。 */
    private final ListingWorkflowService workflowService;

    /**
     * 提交 Listing 资产生成任务。
     *
     * <p>请求使用 multipart/form-data：</p>
     *
     * <ul>
     *   <li>{@code file}：产品资料 Markdown 文件。</li>
     *   <li>{@code productImages}：1-4 张原始产品图。</li>
     *   <li>{@code asins}：可选竞品 ASIN 列表。</li>
     *   <li>{@code marketplace}：站点市场，默认 US。</li>
     *   <li>{@code language}：生成语言，默认 en-US。</li>
     * </ul>
     */
    @PostMapping("/submit")
    public ApiResponse<SubmitListingTaskResponse> submitTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productImages") List<MultipartFile> productImages,
            @RequestParam(value = "asins", required = false) List<String> asins,
            @RequestParam(value = "marketplace", defaultValue = "US") String marketplace,
            @RequestParam(value = "language", defaultValue = "en-US") String language) {
        String taskId = workflowService.submitTask(file, productImages, asins, marketplace, language);
        return ApiResponse.ok(new SubmitListingTaskResponse(taskId));
    }

    /**
     * 查询 Listing 任务详情。
     *
     * <p>该接口只读取任务当前状态和最新 Brief 摘要，不触发生成、审批、归档或导出。</p>
     */
    @GetMapping("/{taskId}")
    public ApiResponse<ListingTaskDetailResponse> getTaskDetail(@PathVariable String taskId) {
        return ApiResponse.ok(workflowService.getTaskDetail(taskId));
    }
}
