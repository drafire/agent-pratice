package com.drafire.service;

import com.drafire.config.KnowledgeBaseVectorStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseVectorStoreManager storeManager;
    private final ChatModel chatModel;

    public KnowledgeBaseService(KnowledgeBaseVectorStoreManager storeManager, ChatModel chatModel) {
        this.storeManager = storeManager;
        this.chatModel = chatModel;
    }

    public String importText(String scope, String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        PgVectorStore store = storeManager.getStore(scope);
        List<Document> documents = List.of(new Document(text));
        List<Document> chunks = new TokenTextSplitter().apply(documents);

        store.add(chunks);
        logger.info("文本导入知识库成功: scope={}, chunks={}", scope, chunks.size());
        return "success";
    }

    public String importFiles(String scope, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("必须上传非空的文件");
        }

        PgVectorStore store = storeManager.getStore(scope);

        List<Document> documentList = new TikaDocumentReader(file.getResource()).get();
        documentList = new ContentFormatTransformer(DefaultContentFormatter.defaultConfig()).apply(documentList);

        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(150)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(2000)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = tokenTextSplitter.apply(documentList);

        KeywordMetadataEnricher keywordMetadataEnricher = KeywordMetadataEnricher.builder(chatModel)
                .keywordCount(3)
                .keywordsTemplate(new PromptTemplate("""
                        你是一个关键词提取器。从以下文本中提取恰好 %s 个最重要的中文关键词。
                        严格要求：只返回 %s 个关键词，以逗号分隔，不要有任何其他内容。
                        文本：
                        {context_str}
                        """))
                .build();
        chunks = keywordMetadataEnricher.apply(chunks);

        chunks.forEach(doc -> {
            Object kw = doc.getMetadata().get("excerpt_keywords");
            if (kw != null) {
                String cleaned = kw.toString()
                        .replaceAll("[\\n\\r]+", ",")
                        .replaceAll("[，、；;]+", ",")
                        .replaceAll("\\d+[、.．]\\s*", "")
                        .replaceAll("^[：:\\s,，]+", "")
                        .replaceAll("[\\s,，]+$", "");
                String[] parts = cleaned.split("\\s*,\\s*");
                List<String> keywords = Arrays.stream(parts)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(3)
                        .toList();
                doc.getMetadata().put("excerpt_keywords", String.join(",", keywords));
            }
        });

        store.add(chunks);
        logger.info("文件导入知识库成功: scope={}, fileName={}, chunks={}",
                scope, file.getOriginalFilename(), chunks.size());
        return "success";
    }
}
