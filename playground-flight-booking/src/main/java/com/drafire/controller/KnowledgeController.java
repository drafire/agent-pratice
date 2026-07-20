package com.drafire.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final VectorStore vectorStore;

    public KnowledgeController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        String category = request.getOrDefault("category", "general");

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "内容不能为空"
            ));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", category);
        metadata.put("source", "manual");

        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "文档已上传到知识库");
        result.put("documentId", document.getId());
        result.put("contentLength", content.length());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload/batch")
    public ResponseEntity<Map<String, Object>> uploadBatchDocuments(
            @RequestBody List<Map<String, String>> documents) {

        if (documents == null || documents.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文档列表不能为空"
            ));
        }

        List<Document> docs = documents.stream()
                .filter(doc -> doc.get("content") != null && !doc.get("content").trim().isEmpty())
                .map(doc -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("category", doc.getOrDefault("category", "general"));
                    metadata.put("source", "manual");
                    return new Document(doc.get("content"), metadata);
                })
                .collect(Collectors.toList());

        vectorStore.add(docs);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "批量上传完成");
        result.put("uploadedCount", docs.size());
        result.put("documentIds", docs.stream().map(Document::getId).collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "查询内容不能为空"
            ));
        }

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        );

        List<Map<String, Object>> formattedResults = results.stream()
                .map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", doc.getId());
                    item.put("content", doc.getText());
                    item.put("metadata", doc.getMetadata());
                    item.put("score", doc.getMetadata().getOrDefault("score", "N/A"));
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("query", query);
        result.put("totalResults", formattedResults.size());
        result.put("results", formattedResults);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable String documentId) {

        try {
            vectorStore.delete(List.of(documentId));

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "文档已删除");
            result.put("documentId", documentId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAll() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "注意：RedisVectorStore 不支持清空操作，需手动删除所有文档或更换 Redis 前缀");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "操作失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("status", "UP");
        result.put("vectorStore", vectorStore.getClass().getSimpleName());
        result.put("message", "知识库服务运行正常");

        return ResponseEntity.ok(result);
    }
}