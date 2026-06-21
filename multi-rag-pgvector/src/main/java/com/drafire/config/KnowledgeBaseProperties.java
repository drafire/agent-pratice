package com.drafire.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "knowledge-base")
public class KnowledgeBaseProperties {

    private List<StoreConfig> stores = new ArrayList<>();

    public List<StoreConfig> getStores() {
        return stores;
    }

    public void setStores(List<StoreConfig> stores) {
        this.stores = stores;
    }

    public static class StoreConfig {
        private String scope;
        private String tableName;
        private int dimensions;
        private String indexType;
        private int topK;
        private double similarityThreshold;

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }

        public String getIndexType() { return indexType; }
        public void setIndexType(String indexType) { this.indexType = indexType; }

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }
}
