package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Listing Brief 版本仓储。
 */
public interface ListingBriefVersionRepository extends JpaRepository<ListingBriefVersion, String> {

    /**
     * 查询指定任务最新创建的 Brief 版本。
     */
    Optional<ListingBriefVersion> findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(String taskId);

    /**
     * 按创建时间和版本 ID 倒序查询指定任务的 Brief 历史。
     */
    List<ListingBriefVersion> findByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(String taskId);
}
