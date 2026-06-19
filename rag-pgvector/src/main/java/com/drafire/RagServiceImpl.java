package com.drafire;

import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Service
public class RagServiceImpl {
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RerankModel rerankModel;

    public RagServiceImpl(VectorStore vectorStore, ChatModel chatModel, RerankModel rerankModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.rerankModel = rerankModel;
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
}
