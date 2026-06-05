package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ProductRawData;
import java.io.InputStream;

/**
 * 产品资料文件解析器端口。
 *
 * <p>Parser 负责文件层面的能力，例如判断文件类型、读取文件内容、把内容交给提取器。
 * 具体业务字段抽取由 {@link ProductDocumentExtractor} 负责，避免把文件读取逻辑和大模型抽取逻辑耦合在一起。</p>
 */
public interface ProductDocumentParser {

    /**
     * 判断当前解析器是否支持指定文件扩展名。
     *
     * @param fileExtension 文件扩展名，不包含点号，例如 md
     * @return 支持返回 true
     */
    boolean supports(String fileExtension);

    /**
     * 解析产品资料文件。
     *
     * @param inputStream 文件输入流
     * @return 结构化产品资料
     */
    ProductRawData parse(InputStream inputStream);
}
