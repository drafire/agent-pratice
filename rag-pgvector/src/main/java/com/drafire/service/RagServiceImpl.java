package com.drafire.service;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class RagServiceImpl {
    private final VectorStore vectorStore;
    private final RerankModel rerankModel;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final ChatClient chatClient;
    private final Resource systemResource;

    @Autowired
    private ChatModel chatModel;

    public RagServiceImpl(VectorStore vectorStore, RerankModel rerankModel
            , ChatClient.Builder chatClientBuilder
            , @Value("classpath:/prompts/system-qa.st") Resource systemResource) {
        this.vectorStore = vectorStore;
        this.rerankModel = rerankModel;
        this.systemResource = systemResource;
        try {
            // 加载系统提示词模板
            String contentAsString = systemResource.getContentAsString(StandardCharsets.UTF_8);
            SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(contentAsString);

            // 定义兜底提示模板
            PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                    抱歉，我在知识库中没有找到关于该问题的相关信息，无法回答。
                    """);

            this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .similarityThreshold(0.3)  // 降低阈值，提高召回率
                            .topK(3)
                            .vectorStore(this.vectorStore)
                            .build())
                    .queryAugmenter(ContextualQueryAugmenter.builder()
                            .promptTemplate(systemPromptTemplate)
                            .emptyContextPromptTemplate(emptyContextPromptTemplate)
                            .allowEmptyContext(false)
                            .build())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize retrieval augmentation advisor", e);
        }

        this.chatClient = chatClientBuilder.build();
    }


    /***
     * 使用阿里云的pgvector，可能需要先安装pgvector的插件
     * SELECT * FROM pg_available_extensions WHERE name = 'vector';
     * SELECT * FROM pg_extension WHERE extname = 'vector';  2
     * 2这里如果为空，则需要执行这个CREATE EXTENSION IF NOT EXISTS vector;
     * @param text
     * @return
     */
    public ResponseEntity<String> insertText(@RequestParam("text") String text) {
        // 1.parameter verification
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().body("Please enter text");
        }

        List<Document> documents = List.of(new Document(text));

        List<Document> chunk = new TokenTextSplitter().apply(documents);
        vectorStore.add(chunk);

        return ResponseEntity.ok("success");
    }

    public ResponseEntity<String> importFiles(MultipartFile file) {
        // 1. file verification
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("必须上传非空的文件");
        }

        //这个文字提取器相当强大，可以支持很多种格式
        List<Document> documentList = new TikaDocumentReader(file.getResource()).get();
        //执行文档的清洗，去掉换行，空格等
        documentList = new ContentFormatTransformer(DefaultContentFormatter.defaultConfig()).apply(documentList);
        //文档切分
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)             // 目标 800 Tokens
                .withMinChunkSizeChars(150)     // 最小 350 字符
                .withMinChunkLengthToEmbed(5)   // 小于 5 个 token 的碎片直接丢弃
                .withMaxNumChunks(2000)        // 单文档最多切 10000 块
                .withKeepSeparator(true)        // 保留换行符等分隔符
                .build();

        List<Document> chunk = tokenTextSplitter.apply(documentList);

        //文档关键字提取（分块后对每个块独立提取关键字，避免所有块共享相同的关键字）
        KeywordMetadataEnricher keywordMetadataEnricher = KeywordMetadataEnricher.builder(chatModel)
                .keywordCount(3)
                // 自定义关键词提取器提示词模板。之所以要这样，是因为默认的关键字是英文，而我们希望提取中文关键词
                .keywordsTemplate(new PromptTemplate("""
                        你是一个关键词提取器。从以下文本中提取恰好 %s 个最重要的中文关键词。
                        严格要求：只返回 %s 个关键词，以逗号分隔，不要有任何其他内容。
                        文本：
                        {context_str}
                        """))
                .build();
        chunk = keywordMetadataEnricher.apply(chunk);
        // 后处理：清洗关键词，确保格式统一且数量受控
        chunk.forEach(doc -> {
            Object kw = doc.getMetadata().get("excerpt_keywords");
            if (kw != null) {
                String cleaned = kw.toString()
                        .replaceAll("[\\n\\r]+", ",")          // 换行转逗号
                        .replaceAll("[，、；;]+", ",")           // 中文标点转逗号
                        .replaceAll("\\d+[、.．]\\s*", "")      // 去掉编号如 "1. "
                        .replaceAll("^[：:\\s,，]+", "")        // 去掉开头冒号
                        .replaceAll("[\\s,，]+$", "");           // 去掉末尾逗号空格
                String[] parts = cleaned.split("\\s*,\\s*");
                List<String> keywords = Arrays.stream(parts)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(3)
                        .toList();
                doc.getMetadata().put("excerpt_keywords", String.join(",", keywords));
            }
        });

        vectorStore.add(chunk);
        return ResponseEntity.ok("success");
    }

    public Flux<String> query(String message) {
        SearchRequest searchRequest = SearchRequest.builder().topK(2).build();
        SystemPromptTemplate userTextAdvise = new SystemPromptTemplate(
                new ClassPathResource("prompts/system-rerank.st"));
//        SystemPromptTemplate userTextAdvise = new SystemPromptTemplate("用户问题: {query}");

        return chatClient.prompt()
                .advisors(new RetrievalRerankAdvisor(vectorStore, rerankModel, searchRequest, userTextAdvise, 0.2))
                .advisors(new SimpleLoggerAdvisor())
                .user(message)
                .stream().content();
    }

    public Flux<String> chatWithDocument(String prompt) {
        System.out.println("prompt:" + prompt);
        return chatClient.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(prompt).stream().content();
    }
}