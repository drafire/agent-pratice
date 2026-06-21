package com.drafire.controller;

import com.drafire.config.KnowledgeBaseProperties;
import com.drafire.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "知识库管理", description = "多知识库的文本与文件导入接口")
@RestController
@RequestMapping("/rag/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseProperties properties;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeBaseProperties properties) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.properties = properties;
    }

    @Operation(summary = "获取所有知识库列表", description = "返回当前配置的所有知识库的 scope、表名、维度、索引类型等信息")
    @GetMapping("/stores")
    public ResponseEntity<Map<String, Object>> listStores() {
        var stores = properties.getStores().stream()
                .map(s -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("scope", s.getScope());
                    info.put("tableName", s.getTableName());
                    info.put("dimensions", s.getDimensions());
                    info.put("indexType", s.getIndexType());
                    return info;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", stores.size());
        result.put("stores", stores);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "导入文本到知识库", description = "将纯文本内容导入到指定 scope 的知识库中，自动进行分词和向量化")
    @PostMapping("/{scope}/import/text")
    public ResponseEntity<String> importText(
            @Parameter(description = "知识库标识", example = "product") @PathVariable String scope,
            @RequestBody String text) {
        try {
            String result = knowledgeBaseService.importText(scope, text);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "上传文件到知识库", description = "将上传的文件（支持 PDF、Word 等格式）解析后导入到指定 scope 的知识库")
    @PostMapping(value = "/{scope}/import/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importFiles(
            @Parameter(description = "知识库标识", example = "product") @PathVariable String scope,
            @RequestPart("file") MultipartFile file) {
        try {
            String result = knowledgeBaseService.importFiles(scope, file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}