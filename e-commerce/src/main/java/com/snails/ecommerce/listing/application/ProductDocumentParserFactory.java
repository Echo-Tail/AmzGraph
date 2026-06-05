package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 产品资料文件解析器工厂。
 *
 * <p>该工厂集中管理多个 {@link ProductDocumentParser} 实现，并根据文件扩展名选择具体解析器。
 * 这样 {@link ListingWorkflowService} 不需要知道当前系统支持哪些文件类型，也不会直接耦合解析器列表。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductDocumentParserFactory {

    /** Spring 注入的所有产品资料文件解析器实现。 */
    private final List<ProductDocumentParser> parsers;

    /**
     * 根据文件扩展名查找支持的解析器。
     *
     * @param fileExtension 文件扩展名，不包含点号，例如 md
     * @return 支持该扩展名的解析器
     * @throws BusinessException 当没有解析器支持该扩展名时抛出
     */
    public ProductDocumentParser getParser(String fileExtension) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileExtension))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FILE_INVALID,
                        "Unsupported product document type: " + fileExtension));
    }
}
