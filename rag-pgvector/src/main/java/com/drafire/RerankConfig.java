package com.drafire;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public RerankModel rerankModel() {
        DashScopeApi dashScopeApi =DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                .withModel("qwen3-rerank")
                .build();
        return new DashScopeRerankModel(dashScopeApi, options);
    }
}
