package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.ProductRawData;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ListingEntityMappingTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ProductRawDataRepository productRawDataRepository;

    @Autowired
    private ListingBriefVersionRepository listingBriefVersionRepository;

    @Autowired
    private ImageAssetRepository imageAssetRepository;

    @Test
    void savesTaskRawDataAndBriefVersion() {
        ListingTask task = new ListingTask();
        task.setTaskId("task_mapping");
        task.setStatus(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        task.setTextStatus(GenerationStatus.NOT_STARTED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(BriefStatus.WAIT_APPROVE);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[\"local/image.png\"]");
        task.setCompetitorAsinsJson("[\"B000TEST\"]");
        listingTaskRepository.save(task);

        ProductRawData rawData = new ProductRawData();
        rawData.setRawDataId("raw_mapping");
        rawData.setTaskId("task_mapping");
        rawData.setProductName("9 Inch Car Stereo");
        rawData.setBrandName("Snails");
        rawData.setCategoryCode("CAR_STEREO");
        rawData.setMarketplace("US");
        rawData.setLanguage("en-US");
        rawData.setCoreFunctionsJson("[\"Wireless CarPlay\"]");
        productRawDataRepository.save(rawData);

        ListingBriefVersion brief = new ListingBriefVersion();
        brief.setBriefVersionId("brief_mapping");
        brief.setTaskId("task_mapping");
        brief.setTargetAudience("Amazon US car stereo buyers");
        brief.setCoreSellingPointsJson("[\"Wireless CarPlay\"]");
        brief.setTargetKeywordsJson("[\"car stereo\"]");
        brief.setCreatedBy("operator@example.com");
        brief.setApproved(true);
        brief.setApprovedBy("reviewer@example.com");
        brief.setApprovedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        listingBriefVersionRepository.save(brief);

        ListingTask savedTask = listingTaskRepository.findById("task_mapping").orElseThrow();
        ProductRawData savedRawData = productRawDataRepository.findByTaskId("task_mapping").orElseThrow();
        ListingBriefVersion savedBrief = listingBriefVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc("task_mapping")
                .orElseThrow();

        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        assertThat(savedRawData.getProductName()).isEqualTo("9 Inch Car Stereo");
        assertThat(savedBrief.getTargetKeywordsJson()).contains("car stereo");
        assertThat(savedBrief.getCreatedBy()).isEqualTo("operator@example.com");
        assertThat(savedBrief.getApprovedBy()).isEqualTo("reviewer@example.com");
        assertThat(savedBrief.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 6, 6, 10, 0));
    }

    @Test
    void savesImageAssetOriginalAndRewrittenPrompts() {
        ImageAsset asset = new ImageAsset();
        asset.setAssetId("asset_prompt_mapping");
        asset.setImageVersionId("img_v1");
        asset.setType(ImageAssetType.MAIN_IMAGE);
        asset.setPrompt("White background car stereo product photo");
        asset.setRewrittenPrompt("Generate a clean Amazon-style white background product photo of the car stereo.");
        imageAssetRepository.save(asset);

        ImageAsset savedAsset = imageAssetRepository.findById("asset_prompt_mapping").orElseThrow();

        assertThat(savedAsset.getPrompt()).contains("White background");
        assertThat(savedAsset.getRewrittenPrompt()).contains("Amazon-style");
    }
}
