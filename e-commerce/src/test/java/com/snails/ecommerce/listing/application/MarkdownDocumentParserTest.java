package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.ProductRawData;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Markdown 产品资料解析器测试。
 *
 * <p>用户上传的产品参数文档格式不可控，因此 Markdown 解析器不直接用固定规则抽取业务字段。
 * 它只负责识别 Markdown、读取原始文本，然后委托产品资料提取器端口完成结构化抽取。该端口后续可由大模型实现。</p>
 */
class MarkdownDocumentParserTest {

    @Test
    void supportsMarkdownExtension() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser(new CapturingProductDocumentExtractor());

        assertThat(parser.supports("md")).isTrue();
        assertThat(parser.supports("MD")).isTrue();
        assertThat(parser.supports("txt")).isFalse();
    }

    @Test
    void delegatesMarkdownContentToProductDataExtractor() {
        CapturingProductDocumentExtractor extractor = new CapturingProductDocumentExtractor();
        MarkdownDocumentParser parser = new MarkdownDocumentParser(extractor);
        String markdown = """
                # 9 Inch Car Stereo

                Brand: Snails

                - Wireless CarPlay
                - Android Auto
                - Bluetooth
                """;

        ProductRawData rawData = parser.parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

        assertThat(extractor.capturedContent).contains("# 9 Inch Car Stereo", "Brand: Snails", "Wireless CarPlay");
        assertThat(rawData.getProductName()).isEqualTo("Extracted Product");
        assertThat(rawData.getCoreFunctionsJson()).isEqualTo("[\"AI extracted selling point\"]");
    }

    /**
     * 测试替身：用于证明 MarkdownDocumentParser 会把完整 Markdown 原文交给提取器端口。
     */
    private static class CapturingProductDocumentExtractor implements ProductDocumentExtractor {

        private String capturedContent;

        @Override
        public ProductRawData extract(String content) {
            capturedContent = content;
            ProductRawData rawData = new ProductRawData();
            rawData.setProductName("Extracted Product");
            rawData.setCoreFunctionsJson("[\"AI extracted selling point\"]");
            return rawData;
        }
    }
}
