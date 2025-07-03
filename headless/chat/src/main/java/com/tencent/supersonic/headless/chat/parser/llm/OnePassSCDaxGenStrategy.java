package com.tencent.supersonic.headless.chat.parser.llm;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.pojo.enums.AppModule;
import com.tencent.supersonic.common.util.ChatAppManager;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMResp;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dax sql generation strategy
 * 
 * @author: mroldx
 */
@Service
@Slf4j
public class OnePassSCDaxGenStrategy extends SqlGenStrategy {

    private static final Logger daxPipelineLog = LoggerFactory.getLogger("daxPipeline");

    public static final String APP_KEY = "S2DAX_PARSER";
    public static final String INSTRUCTION =
            "#Role: You are a data analyst experienced in DAX (Data Analysis Expressions) language."
                    + "\n#Task:"
                    + " You will be provided with a natural language question asked by users,"
                    + "please convert it to a DAX query so that relevant data could be returned "
                    + "by executing the DAX query against underlying data model." + "\n#Rules:"
                    + "\n1.DAX measures and columns must be mentioned in the `Schema`, DO NOT hallucinate."
                    + "\n2.ALWAYS specify time range using `>`,`<`,`>=`,`<=` operator."
                    + "\n3.DO NOT include time range in the where clause if not explicitly expressed in the `Question`."
                    + "\n4.DO NOT calculate date range using functions."
                    + "\n5.ALWAYS use `with` statement if nested aggregation is needed."
                    + "\n6.ALWAYS enclose alias declared by `AS` command in underscores."
                    + "\n7.Alias created by `AS` command must be in the same language ast the `Question`."
                    + "\n#Exemplars: {{exemplar}}"
                    + "\n#Query: Question:{{question}},Schema:{{schema}},SideInfo:{{information}}";

    public OnePassSCDaxGenStrategy() {
        ChatAppManager.register(APP_KEY, ChatApp.builder().prompt(INSTRUCTION).name("语义DAX解析")
                .appModule(AppModule.CHAT).description("通过大模型做语义解析生成S2DAX").enable(true).build());
    }

    @Data
    static class SemanticDax {
        @Description("thought or remarks to tell users about the dax, make it short.")
        private String thought;

        @Description("dax to generate")
        private String dax;
    }

    interface SemanticDaxExtractor {
        SemanticDax generateSemanticDax(String text);
    }

    @Override
    public LLMResp generate(LLMReq llmReq) {
        LLMResp llmResp = new LLMResp();
        llmResp.setQuery(llmReq.getQueryText());
        // 1.recall exemplars
        //log.debug("OnePassSCDaxGenStrategy llmReq:\n{}", llmReq);
        List<List<Text2SQLExemplar>> exemplarsList = promptHelper.getFewShotExemplars(llmReq);

        // 2.generate sql generation prompt for each self-consistency inference
        ChatApp chatApp = llmReq.getChatAppConfig().get(APP_KEY);
        ChatLanguageModel chatLanguageModel = getChatLanguageModel(chatApp.getChatModelConfig());
        // 声明式方式 使用AiServices 创建代理对象
        SemanticDaxExtractor extractor =
                AiServices.create(SemanticDaxExtractor.class, chatLanguageModel);

        Map<Prompt, List<Text2SQLExemplar>> prompt2Exemplar = new HashMap<>();
        for (List<Text2SQLExemplar> exemplars : exemplarsList) {
            llmReq.setDynamicExemplars(exemplars);
            // 将系统内置示例集拼接成prompt
            Prompt prompt = generatePrompt(llmReq, llmResp, chatApp);
            prompt2Exemplar.put(prompt, exemplars);
        }

        // 3.perform multiple self-consistency inferences parallelly
        Map<String, Prompt> output2Prompt = new ConcurrentHashMap<>();
        prompt2Exemplar.keySet().parallelStream().forEach(prompt -> {
            SemanticDax s2Dax = extractor.generateSemanticDax(prompt.toUserMessage().singleText());
            output2Prompt.put(s2Dax.getDax(), prompt);
            daxPipelineLog.debug("OnePassSCDaxGenStrategy modelReq:\n{} \nmodelResp:\n{}",
                    prompt.text(), s2Dax);
            daxPipelineLog.info("OnePassSCDaxGenStrategy thought:\n{}", s2Dax.getThought());
        });

        // 4.format response.
        Pair<String, Map<String, Double>> sqlMapPair =
                ResponseHelper.selfConsistencyVote(Lists.newArrayList(output2Prompt.keySet()));
        llmResp.setSqlOutput(sqlMapPair.getLeft());
        List<Text2SQLExemplar> usedExemplars =
                prompt2Exemplar.get(output2Prompt.get(sqlMapPair.getLeft()));
        llmResp.setSqlRespMap(ResponseHelper.buildSqlRespMap(usedExemplars, sqlMapPair.getRight()));

        return llmResp;
    }

    private Prompt generatePrompt(LLMReq llmReq, LLMResp llmResp, ChatApp chatApp) {
        StringBuilder exemplars = new StringBuilder();
        for (Text2SQLExemplar exemplar : llmReq.getDynamicExemplars()) {
            String exemplarStr = String.format("\nQuestion:%s,Schema:%s,SideInfo:%s,SQL:%s",
                    exemplar.getQuestion(), exemplar.getDbSchema(), exemplar.getSideInfo(),
                    exemplar.getSql());
            exemplars.append(exemplarStr);
        }
        String dataSemantics = promptHelper.buildDaxSchemaStr(llmReq);
        log.info("-----------------------dax llm schema: \n {}", dataSemantics);
        String sideInformation = promptHelper.buildSideInformation(llmReq);
        llmResp.setSchema(dataSemantics);
        llmResp.setSideInfo(sideInformation);

        Map<String, Object> variable = new HashMap<>();
        variable.put("exemplar", exemplars);
        variable.put("question", llmReq.getQueryText());
        variable.put("schema", dataSemantics);
        variable.put("information", sideInformation);

        // use custom prompt template if provided.
        String promptTemplate = chatApp.getPrompt();
        return PromptTemplate.from(promptTemplate).apply(variable);
    }

    @Override
    public void afterPropertiesSet() {
        SqlGenStrategyFactory.addSqlGenerationForFactory(
                LLMReq.SqlGenType.ONE_PASS_SELF_CONSISTENCY_WITH_DAX, this);
    }
}
