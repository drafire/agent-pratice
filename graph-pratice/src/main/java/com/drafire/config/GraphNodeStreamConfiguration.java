package com.drafire.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.drafire.node.ExpanderNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class GraphNodeStreamConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphNodeStreamConfiguration.class);

    @Bean
    public StateGraph streamGraph(ChatClient.Builder builder) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                .addPatternStrategy("query", new ReplaceStrategy())
                .addPatternStrategy("expander_number", new ReplaceStrategy())
                .addPatternStrategy("expander_content", new ReplaceStrategy())
                .build();

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode("expander", node_async(new ExpanderNode(builder)))
                .addEdge(StateGraph.START, "expander")
                .addEdge("expander", StateGraph.END);

        // 添加 PlantUML 打印
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "expander flow");
        logger.info("\n=== expander UML Flow ===");
        logger.info(representation.content());
        logger.info("==================================\n");

        return stateGraph;
    }
}
