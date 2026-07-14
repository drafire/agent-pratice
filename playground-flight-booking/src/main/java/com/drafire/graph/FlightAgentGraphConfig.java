package com.drafire.graph;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.drafire.config.GraphMetrics;
import com.drafire.graph.node.ExecuteActionNode;
import com.drafire.graph.node.IntentClassifierNode;
import com.drafire.graph.node.MdcPropagatingNodeAction;
import com.drafire.graph.node.ParameterExtractorNode;
import com.drafire.graph.node.ResponseGeneratorNode;
import com.drafire.interceptor.ResponseGuard;
import com.drafire.interceptor.ResponseRenderer;
import com.drafire.security.ActionTokenService;
import com.drafire.serivce.FlightBookingService;
import com.drafire.serivce.FlightSearchService;
import com.drafire.serivce.WeatherService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Configuration
public class FlightAgentGraphConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlightAgentGraphConfig.class);

    private final ChatClient chatClient;
    private final FlightBookingService flightBookingService;
    private final FlightSearchService flightSearchService;
    private final WeatherService weatherService;
    private final ResponseRenderer responseRenderer;
    private final ResponseGuard responseGuard;
    private final ActionTokenService actionTokenService;
    private final GraphMetrics graphMetrics;
    private final MeterRegistry registry;

    public FlightAgentGraphConfig(
                                  ChatClient chatClient,
                                  FlightBookingService flightBookingService,
                                  FlightSearchService flightSearchService,
                                  WeatherService weatherService,
                                  ResponseRenderer responseRenderer,
                                  ResponseGuard responseGuard,
                                  ActionTokenService actionTokenService, GraphMetrics graphMetrics, MeterRegistry registry) {
        this.chatClient = chatClient;
        this.flightBookingService = flightBookingService;
        this.flightSearchService = flightSearchService;
        this.weatherService = weatherService;
        this.responseRenderer = responseRenderer;
        this.responseGuard = responseGuard;
        this.actionTokenService = actionTokenService;
        this.graphMetrics = graphMetrics;
        this.registry = registry;
    }

    @Bean
    public StateGraph flightAgentStateGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategy = new KeyStrategyFactoryBuilder()
                .addPatternStrategy("userInput", new ReplaceStrategy())
                .addPatternStrategy("intent", new ReplaceStrategy())
                .addPatternStrategy("bookingNumber", new ReplaceStrategy())
                .addPatternStrategy("customerName", new ReplaceStrategy())
                .addPatternStrategy("from", new ReplaceStrategy())
                .addPatternStrategy("to", new ReplaceStrategy())
                .addPatternStrategy("date", new ReplaceStrategy())
                .addPatternStrategy("weatherCity", new ReplaceStrategy())
                .addPatternStrategy("weatherDays", new ReplaceStrategy())
                .addPatternStrategy("toolResult", new ReplaceStrategy())
                .addPatternStrategy("reply", new ReplaceStrategy())
                .addPatternStrategy("cancelConfirmed", new ReplaceStrategy())
                .addPatternStrategy("chatId", new ReplaceStrategy())
                .addPatternStrategy("_mdc", new ReplaceStrategy())
                .build();

        StateGraph graph = new StateGraph(keyStrategy)
                .addNode("classify_intent", new MdcPropagatingNodeAction(new IntentClassifierNode(chatClient,graphMetrics),registry,"classify_intent"))
                .addNode("extract_params", new MdcPropagatingNodeAction(new ParameterExtractorNode(chatClient, graphMetrics),registry,"extract_params"))
                .addNode("execute_action", new MdcPropagatingNodeAction(new ExecuteActionNode(
                        flightBookingService, flightSearchService, weatherService,
                        responseRenderer, responseGuard, actionTokenService),registry,"execute_action"))
                .addNode("generate_response", new MdcPropagatingNodeAction(new ResponseGeneratorNode(chatClient, responseGuard, graphMetrics),registry,"generate_response"))

                .addEdge(START, "classify_intent")
                .addEdge("classify_intent", "extract_params")
                .addEdge("extract_params", "execute_action")
                .addEdge("execute_action", "generate_response")
                .addEdge("generate_response", END);

        GraphRepresentation representation = graph.getGraph(
                GraphRepresentation.Type.PLANTUML, "Flight Agent Graph");
        logger.info("\n=== Flight Agent 工作流 ===\n{}\n========================",
                representation.content());

        return graph;
    }
}