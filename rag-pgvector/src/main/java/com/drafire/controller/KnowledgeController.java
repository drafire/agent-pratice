package com.drafire.controller;

import com.drafire.service.KnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {
    @Autowired
    private KnowledgeService knowledgeBaseService;
    /**
     * 在指定业务类型的知识库中执行相似性搜索。
     *
     * @param query 搜索查询
     * @param topK  要检索的相似文档数量（默认为5）
     * @return 包含相似文档列表或错误消息的响应实体
     */
    @GetMapping("/search")
    public ResponseEntity<?> similaritySearch(@RequestParam("query") String query,
                                              @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("查询内容是必需的");
        }
        if (topK <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("topK必须是正整数");
        }

        try {
            List<Document> results = knowledgeBaseService.similaritySearch(query, topK);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("相似性搜索过程中发生错误: " + e.getMessage());
        }
    }
}
