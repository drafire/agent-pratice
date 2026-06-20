package com.drafire;

import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class RagServiceImpl {
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RerankModel rerankModel;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final ChatClient chatClient;

    private final Resource systemResource;

    public RagServiceImpl(VectorStore vectorStore, ChatModel chatModel, RerankModel rerankModel, ChatClient.Builder chatClientBuilder, @Value("classpath:/prompts/system-qa.st") Resource systemResource) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
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
                            .similarityThreshold(0.5)  // 降低阈值，提高召回率
                            .topK(5)
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

        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)             // 目标 800 Tokens
                .withMinChunkSizeChars(150)     // 最小 350 字符
                .withMinChunkLengthToEmbed(5)   // 小于 5 个 token 的碎片直接丢弃
                .withMaxNumChunks(2000)        // 单文档最多切 10000 块
                .withKeepSeparator(true)        // 保留换行符等分隔符
                .build();

        List<Document> chunk = tokenTextSplitter.apply(documentList);

        vectorStore.add(chunk);
        return ResponseEntity.ok("success");
    }

    public Flux<ChatResponse> query(String message) throws IOException {
        String contentAsString = systemResource.getContentAsString(StandardCharsets.UTF_8);
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(contentAsString);

        // 2. 定义当检索结果为空时的兜底提示模板
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                抱歉，我在知识库中没有找到关于该问题的相关信息，无法回答。
                """);

        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.8)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(systemPromptTemplate)   //这个类似于RetrievalRerankAdvisor的prompt
                        .emptyContextPromptTemplate(emptyContextPromptTemplate)
                        .allowEmptyContext(false) // 不允许空上下文，触发兜底提示
                        .build())
                .build();

        return ChatClient.builder(chatModel)
                //.defaultAdvisors(new RetrievalRerankAdvisor(vectorStore, rerankModel, searchRequest, systemPromptTemplate, 0.8))
                .defaultAdvisors(retrievalAugmentationAdvisor)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build()
                .prompt()
                .user(message)
                .stream().chatResponse()
                ;
    }

    public String chatWithDocument(String prompt) {
        System.out.println("prompt:" + prompt);
        return chatClient.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(prompt).call().content();
    }
}
