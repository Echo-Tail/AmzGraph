package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ProductRawData;

/**
 * 产品资料结构化提取端口。
 *
 * <p>用户上传的产品参数文档格式不统一，不能依赖固定模板或简单字符串规则直接解析业务字段。
 * 该端口负责把非结构化文档内容抽取为 {@link ProductRawData}。第一版推荐由大模型实现，
 * 也可以在后续增加规则实现作为固定模板场景下的快速路径或降级方案。</p>
 */
public interface ProductDocumentExtractor {

    /**
     * 从原始文档内容中提取结构化产品资料。
     *
     * @param content 原始文档文本内容
     * @return 结构化产品资料
     */
    ProductRawData extract(String content);
}
