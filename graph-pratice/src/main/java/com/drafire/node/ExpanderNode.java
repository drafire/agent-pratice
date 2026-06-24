package com.drafire.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

public class ExpanderNode implements NodeAction {
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("You are an expert at information retrieval and search optimization.\nYour task is to generate {number} different versions of the given query.\n\nEach variant must cover different perspectives or aspects of the topic,\nwhile maintaining the core intent of the original query. The goal is to\nexpand the search space and improve the chances of finding relevant information.\n\nDo not explain your choices or add any other text.\nProvide the query variants separated by newlines.\n\nOriginal query: {query}\n\nQuery variants:\n");

    private final ChatClient chatClient;

    private final Integer NUMBER = 3;

    public ExpanderNode(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String query = state.value("query", "");
        Integer expanderNumber = state.value("expander_number", this.NUMBER);
        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt().user(promptUserSpec -> promptUserSpec.text(DEFAULT_PROMPT_TEMPLATE.getTemplate())    //注意，要先写这个text
                        .param("query", query)
                        .param("number", expanderNumber))
                .stream().chatResponse();



        return Map.of("expander_content",chatResponseFlux);
    }
}
