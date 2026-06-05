package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.listing.domain.ProductRawData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Markdown 产品资料文件解析器。
 *
 * <p>该类只处理文件层面的工作：判断扩展名、读取 Markdown 原文，然后把完整文本交给
 * {@link ProductDocumentExtractor}。它不使用固定字符串规则抽取产品字段，因为运营上传的资料格式可能不统一。</p>
 */
@Component
@RequiredArgsConstructor
public class MarkdownDocumentParser implements ProductDocumentParser {

    /** 产品资料结构化提取端口，后续可由大模型实现。 */
    private final ProductDocumentExtractor extractor;

    /**
     * Markdown 文件扩展名匹配，不区分大小写。
     */
    @Override
    public boolean supports(String fileExtension) {
        return "md".equalsIgnoreCase(fileExtension);
    }

    /**
     * 读取 Markdown 原文，并委托提取器生成结构化产品资料。
     */
    @Override
    public ProductRawData parse(InputStream inputStream) {
        try {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return extractor.extract(content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read markdown product document", e);
        }
    }
}
