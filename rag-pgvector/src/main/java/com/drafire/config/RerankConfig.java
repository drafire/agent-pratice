package com.drafire.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankConfig {

//不需要这个config了。原来写的这个config，是因为没有指定rerank模型，导致默认是gte-rerank模型，也没有正确配置application.yml

//    @Value("${spring.ai.dashscope.api-key}")
//    private String apiKey;
//
//    @Bean
//    public RerankModel rerankModel() {
//        DashScopeApi dashScopeApi =DashScopeApi.builder()
//                .apiKey(apiKey)
//                .build();
//        //之所以选择qwen3-rerank是因为qwen3-rerank是qwen3的rerank模型。如果不配置这个，默认是gte-rerank模型，且不可以通过application.yml配置模型
//        DashScopeRerankOptions options = DashScopeRerankOptions.builder()
//                .withModel("qwen3-rerank")
//                .build();
//        return new DashScopeRerankModel(dashScopeApi, options);
//    }
}
