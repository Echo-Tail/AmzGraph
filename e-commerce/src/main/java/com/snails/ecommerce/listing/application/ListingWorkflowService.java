package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.storage.FileStoragePort;
import com.snails.ecommerce.common.storage.StoredFile;
import com.snails.ecommerce.listing.api.ListingTaskDetailResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.ProductRawData;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.ProductRawDataRepository;
import com.snails.ecommerce.template.application.CategoryTemplateService;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Listing 工作流应用服务。
 *
 * <p>第一阶段只实现任务提交最小闭环：校验上传文件、保存原始文件、读取类目模板、解析产品资料、
 * 创建待审核 Brief，并把任务推进到 Brief 待审核状态。</p>
 */
@Service
@RequiredArgsConstructor
public class ListingWorkflowService {

    /** 第一版默认类目模板。 */
    private static final String DEFAULT_CATEGORY_CODE = "CAR_STEREO";

    /** 任务主记录仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** 产品资料解析结果仓储。 */
    private final ProductRawDataRepository productRawDataRepository;

    /** Brief 版本仓储。 */
    private final ListingBriefVersionRepository listingBriefVersionRepository;

    /** 类目模板服务。 */
    private final CategoryTemplateService categoryTemplateService;

    /** 产品资料文件解析器工厂，根据文件扩展名选择具体解析器。 */
    private final ProductDocumentParserFactory productDocumentParserFactory;

    /** 文件存储端口。 */
    private final FileStoragePort fileStorage;

    /** 业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /**
     * 提交 Listing 任务，并创建第一阶段最小闭环数据。
     *
     * <p>提交过程包含以下步骤：</p>
     *
     * <ol>
     *   <li>校验产品资料文件是否存在，并通过 {@link ProductDocumentParserFactory} 按文件扩展名选择解析器。</li>
     *   <li>校验原始产品图数量必须为 1-4，且每个文件 MIME 类型必须是 image/*。</li>
     *   <li>读取默认 {@code CAR_STEREO} 类目模板，确认当前站点和语言有可用模板。</li>
     *   <li>保存产品资料文件和产品图片到 {@link FileStoragePort}，图片文件键写入任务记录。</li>
     *   <li>调用选中的文件解析器解析产品资料。解析器会把原文交给 {@link ProductDocumentExtractor}，
     *       后续真实实现由大模型完成结构化抽取。</li>
     *   <li>保存 {@link ProductRawData}，补齐任务 ID、类目、站点和语言。</li>
     *   <li>创建 {@link ListingTask}，状态进入 {@link ListingTaskStatus#WAIT_BRIEF_APPROVE}，
     *       文案和图片生成状态保持 {@link GenerationStatus#NOT_STARTED}。</li>
     *   <li>创建第一阶段占位 {@link ListingBriefVersion}，用于支撑前端和流程闭环；真实 Brief 生成后续替换。</li>
     * </ol>
     *
     * @param documentFile 产品资料 Markdown 文件
     * @param productImages 原始产品图片，数量必须为 1-4
     * @param asins 竞品 ASIN，第一阶段只保存输入，不发起外部采集
     * @param marketplace 站点市场，第一版默认 US
     * @param language 生成语言，第一版默认 en-US
     * @return 新任务 ID
     */
    @Transactional
    public String submitTask(
            MultipartFile documentFile,
            List<MultipartFile> productImages,
            List<String> asins,
            String marketplace,
            String language) {
        ProductDocumentParser documentParser = selectDocumentParser(documentFile);
        validateProductImages(productImages);

        String resolvedMarketplace = StringUtils.hasText(marketplace) ? marketplace : "US";
        String resolvedLanguage = StringUtils.hasText(language) ? language : "en-US";
        CategoryTemplate template = categoryTemplateService.getEnabledTemplate(
                DEFAULT_CATEGORY_CODE,
                resolvedMarketplace,
                resolvedLanguage);

        saveMultipartFile("uploads/documents", documentFile);
        List<String> productImageUrls = productImages.stream()
                .map(image -> saveMultipartFile("uploads/product-images", image))
                .map(StoredFile::fileKey)
                .toList();

        ProductRawData rawData = parseProductRawData(documentParser, documentFile);
        String taskId = idGenerator.generate("task");
        rawData.setRawDataId(idGenerator.generate("raw"));
        rawData.setTaskId(taskId);
        rawData.setCategoryCode(DEFAULT_CATEGORY_CODE);
        rawData.setMarketplace(resolvedMarketplace);
        rawData.setLanguage(resolvedLanguage);
        productRawDataRepository.save(rawData);

        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        task.setTextStatus(GenerationStatus.NOT_STARTED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(BriefStatus.WAIT_APPROVE);
        task.setCategoryCode(DEFAULT_CATEGORY_CODE);
        task.setCategoryTemplateId(template.getTemplateId());
        task.setMarketplace(resolvedMarketplace);
        task.setLanguage(resolvedLanguage);
        task.setOriginalProductUrlsJson(toJsonStringArray(productImageUrls));
        task.setCompetitorAsinsJson(toJsonStringArray(asins));
        listingTaskRepository.save(task);

        ListingBriefVersion brief = createPlaceholderBrief(taskId, rawData);
        listingBriefVersionRepository.save(brief);

        return taskId;
    }

    /**
     * 查询 Listing 任务详情。
     *
     * <p>该查询不触发任何生成、审批、归档或导出副作用，只组装当前数据库中已经存在的任务主状态、
     * 输入信息和最新 Brief 摘要。</p>
     *
     * @param taskId 任务 ID
     * @return 任务详情响应
     * @throws BusinessException 当任务不存在时抛出
     */
    @Transactional(readOnly = true)
    public ListingTaskDetailResponse getTaskDetail(String taskId) {
        ListingTask task = listingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "Listing task not found: " + taskId));
        ListingBriefVersion latestBrief = listingBriefVersionRepository
                .findTopByTaskIdOrderByCreatedAtDesc(taskId)
                .orElse(null);

        return ListingTaskDetailResponse.from(
                task,
                latestBrief,
                parseJsonStringArray(task.getOriginalProductUrlsJson()),
                parseJsonStringArray(task.getCompetitorAsinsJson()));
    }

    /**
     * 根据产品资料文件扩展名选择解析器。
     */
    private ProductDocumentParser selectDocumentParser(MultipartFile documentFile) {
        if (documentFile == null || documentFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Product document is required");
        }
        String extension = getExtension(documentFile.getOriginalFilename());
        return productDocumentParserFactory.getParser(extension);
    }

    /**
     * 校验原始产品图数量和 MIME 类型。
     */
    private void validateProductImages(List<MultipartFile> productImages) {
        if (productImages == null || productImages.isEmpty() || productImages.size() > 4) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Product images count must be between 1 and 4");
        }
        for (MultipartFile image : productImages) {
            if (image == null || image.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_INVALID, "Product image cannot be empty");
            }
            String contentType = image.getContentType();
            if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
                throw new BusinessException(ErrorCode.FILE_INVALID, "Product image must be an image file");
            }
        }
    }

    /**
     * 保存上传文件到文件存储。
     */
    private StoredFile saveMultipartFile(String namespace, MultipartFile file) {
        try {
            return fileStorage.save(namespace, file.getOriginalFilename(), file.getContentType(), file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Failed to read uploaded file");
        }
    }

    /**
     * 调用产品资料解析器生成结构化产品资料。
     */
    private ProductRawData parseProductRawData(ProductDocumentParser documentParser, MultipartFile documentFile) {
        try {
            return documentParser.parse(documentFile.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Failed to read product document");
        }
    }

    /**
     * 创建第一阶段占位 Brief。
     *
     * <p>后续接入真实 Brief 生成时，这里会替换为大模型生成和人工审核链路。</p>
     */
    private ListingBriefVersion createPlaceholderBrief(String taskId, ProductRawData rawData) {
        ListingBriefVersion brief = new ListingBriefVersion();
        brief.setBriefVersionId(idGenerator.generate("brief"));
        brief.setTaskId(taskId);
        brief.setTargetAudience("Amazon US car stereo buyers");
        brief.setCoreSellingPointsJson(StringUtils.hasText(rawData.getCoreFunctionsJson())
                ? rawData.getCoreFunctionsJson()
                : "[]");
        brief.setTargetKeywordsJson(toJsonStringArray(List.of("car stereo", "wireless carplay", "android auto")));
        brief.setForbiddenClaimsJson("[]");
        brief.setImageDirectionPromptsJson("[]");
        brief.setComplianceNotesJson(toJsonStringArray(List.of("Generated from placeholder brief in phase 1.")));
        brief.setApproved(false);
        return brief;
    }

    /**
     * 获取文件扩展名。
     */
    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 && dotIndex < filename.length() - 1 ? filename.substring(dotIndex + 1) : "";
    }

    /**
     * 将字符串列表序列化为简单 JSON 数组。
     */
    private String toJsonStringArray(List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        List<String> jsonItems = new ArrayList<>();
        for (String value : safeValues) {
            if (StringUtils.hasText(value)) {
                jsonItems.add("\"" + escapeJson(value.trim()) + "\"");
            }
        }
        return "[" + String.join(",", jsonItems) + "]";
    }

    /**
     * 解析当前服务生成的简单 JSON 字符串数组。
     *
     * <p>第一阶段只解析 {@link #toJsonStringArray(List)} 生成的数组格式，不作为通用 JSON 解析器使用。</p>
     */
    private List<String> parseJsonStringArray(String json) {
        if (!StringUtils.hasText(json) || "[]".equals(json.trim())) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of();
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (!StringUtils.hasText(body)) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (ch == ',' && !inString) {
                addParsedJsonValue(values, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        addParsedJsonValue(values, current);
        return values;
    }

    /**
     * 添加解析出的 JSON 数组元素。
     */
    private void addParsedJsonValue(List<String> values, StringBuilder current) {
        String value = current.toString().trim();
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     */
    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
