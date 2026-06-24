package com.drafire.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.drafire.controller.process.GraphProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@RestController
@RequestMapping("/graph/stream")
public class GraphController {
    private static final Logger logger = LoggerFactory.getLogger(GraphController.class);

    private final CompiledGraph compiledGraph;

    public GraphController(@Qualifier("streamGraph") StateGraph stateGraph) throws GraphStateException {
        this.compiledGraph = stateGraph.compile();
    }

    @GetMapping(value = "/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<GraphProcess.ChatMessage>> expand(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？", required = false) String query,
                                                                  @RequestParam(value = "expander_number", defaultValue = "3", required = false) Integer expanderNumber,
                                                                  @RequestParam(value = "thread_id", defaultValue = "yingzi", required = false) String threadId) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("query", query);
        objectMap.put("expander_number", expanderNumber);

        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();

        Flux<NodeOutput> stream = compiledGraph.stream(objectMap, runnableConfig);

        GraphProcess graphProcess = new GraphProcess(this.compiledGraph);
        Sinks.Many<ServerSentEvent<GraphProcess.ChatMessage>> sink = Sinks.many().unicast().onBackpressureBuffer();

        graphProcess.processStream(stream, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("cancel"))
                .doOnError(throwable -> logger.info("error:{}", throwable))
                ;
    }
}
