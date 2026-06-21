package com.drafire.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("多知识库 RAG 系统 API")
                        .description("基于 Spring AI + pgvector 的多知识库检索增强生成系统，支持文本导入、文件上传、向量化存储与相似度检索")
                        .version("1.0.0"));
    }
}
