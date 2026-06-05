package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.domain.ProductRawData;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 产品资料文件解析器工厂测试。
 *
 * <p>工厂负责根据文件扩展名选择具体 {@link ProductDocumentParser} 实现，避免工作流服务直接耦合解析器列表。</p>
 */
class ProductDocumentParserFactoryTest {

    @Test
    void returnsParserThatSupportsExtension() {
        ProductDocumentParser unsupportedParser = new StubProductDocumentParser(false);
        ProductDocumentParser markdownParser = new StubProductDocumentParser(true);
        ProductDocumentParserFactory factory = new ProductDocumentParserFactory(
                List.of(unsupportedParser, markdownParser));

        ProductDocumentParser selectedParser = factory.getParser("md");

        assertThat(selectedParser).isSameAs(markdownParser);
    }

    @Test
    void throwsBusinessExceptionWhenNoParserSupportsExtension() {
        ProductDocumentParserFactory factory = new ProductDocumentParserFactory(
                List.of(new StubProductDocumentParser(false)));

        assertThatThrownBy(() -> factory.getParser("pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_INVALID);
    }

    /**
     * 测试替身：通过构造参数控制是否支持目标扩展名。
     */
    private record StubProductDocumentParser(boolean supported) implements ProductDocumentParser {

        @Override
        public boolean supports(String fileExtension) {
            return supported;
        }

        @Override
        public ProductRawData parse(InputStream inputStream) {
            return new ProductRawData();
        }
    }
}
