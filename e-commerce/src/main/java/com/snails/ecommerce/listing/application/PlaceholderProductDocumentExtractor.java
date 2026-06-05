package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ProductRawData;
import org.springframework.stereotype.Component;

/**
 * 第一阶段占位产品资料提取器。
 *
 * <p>该实现只用于保证当前后端骨架可以启动和完成最小闭环。它不尝试用固定规则解析用户文档，
 * 因为用户提供的产品参数文档格式不统一。后续接入 Spring AI Alibaba 后，应由大模型实现替换该占位实现。</p>
 */
@Component
public class PlaceholderProductDocumentExtractor implements ProductDocumentExtractor {

    /**
     * 返回空结构化结果。
     *
     * <p>空数组字段用于避免后续占位 Brief 生成时出现 null。真实字段抽取由后续大模型实现负责。</p>
     */
    @Override
    public ProductRawData extract(String content) {
        ProductRawData rawData = new ProductRawData();
        rawData.setSpecificationsJson("{}");
        rawData.setCoreFunctionsJson("[]");
        rawData.setPackageItemsJson("[]");
        rawData.setCompatibilityInfoJson("[]");
        rawData.setForbiddenClaimsJson("[]");
        return rawData;
    }
}
