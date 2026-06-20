package com.drafire;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagServiceImpl ragService;

    public RagController(RagServiceImpl ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/import/text")
    public ResponseEntity<String> importText(@RequestParam(defaultValue = "核心功能模块\n" +
            "2.1 智能问答引擎：支持自然语言提问，系统会自动解析用户意图，并从知识库中检索最相关的文档片段，生成准确且带有引用来源的回答。\n" +
            "2.2 多格式文档解析：支持导入 PDF、Word、Markdown 以及纯文本（TXT）等多种格式的文件。系统会自动进行文本清洗、分块（Chunking）并向量化处理。\n" +
            "2.3 权限管理控制：采用 RBAC（基于角色的访问控制）模型，确保不同部门的员工只能检索和查看其权限范围内的知识内容，保障企业数据安全。\n") String message) {
        return ragService.insertText(message);
    }


    @PostMapping("/import/files")
    public ResponseEntity<String> importFiles(@RequestPart(value = "file", required = false) MultipartFile file) {
        return ragService.importFiles(file);
    }

    @GetMapping(value = "/query", produces = "text/plain;charset=UTF-8")
    public Flux<String> query(@RequestParam(value = "message",
            defaultValue = "帮我分析美的空调的优缺点") String message) throws IOException {

        return ragService.chatWithDocument(message);
    }
}
