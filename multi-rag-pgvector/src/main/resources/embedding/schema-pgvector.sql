-- E:\workspace\agent-pratice\multi-rag-pgvector\src\main\resources\schema-pgvector.sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ${tableName} (
    id uuid PRIMARY KEY,
    content text,
    metadata jsonb,
    embedding vector(${dimensions})
);
