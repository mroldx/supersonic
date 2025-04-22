package com.tencent.supersonic.headless.chat.parser.llm;

import com.tencent.supersonic.common.pojo.ChatModelConfig;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import com.tencent.supersonic.headless.chat.parser.SemanticParser;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMResp;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMSqlResp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * LLMSqlParser 使用大语言模型（LLM）理解查询语义，并生成 S2SQL 语句，供语义查询引擎执行。
 * LLMSqlParser uses large language model to understand query semantics and generate S2SQL
 * statements to be executed by the semantic query engine.
 */
@Slf4j
public class LLMSqlParser implements SemanticParser {

    /**
     * 解析查询上下文，使用大语言模型生成 S2SQL 语句。
     * 该方法根据查询上下文决定是否跳过解析，获取数据集 ID，并调用 LLM 服务进行解析。
     *
     * @param queryCtx 包含查询文本和上下文的查询上下文对象。
     */
    @Override
    public void parse(ChatQueryContext queryCtx) {
        try {
            // 1.determine whether to skip this parser.
            // 1. 判断是否需要跳过当前解析器。
            if (!queryCtx.getRequest().getText2SQLType().enableLLM()) {
                return;
            }
            // 2.get dataSetId from queryCtx and chatCtx.
            // 2. 从查询上下文和聊天上下文中获取数据集 ID。
            LLMRequestService requestService = ContextUtils.getBean(LLMRequestService.class);
            Long dataSetId = requestService.getDataSetId(queryCtx);
            if (dataSetId == null) {
                return;
            }
            log.info("try generating query statement for query:{}, dataSetId:{}",
                    queryCtx.getRequest().getQueryText(), dataSetId);

            // 3.invoke LLM service to do parsing.
            // 3. 调用 LLM 服务进行解析。
            tryParse(queryCtx, dataSetId);
        } catch (Exception e) {
            log.error("failed to parse query:", e);
        }
    }

    /**
     * 尝试解析查询上下文，使用大语言模型生成 S2SQL 语句。
     * 该方法会在最大重试次数内调用 LLM 服务，并对生成的 S2SQL 结果进行去重和解析。
     *
     * @param queryCtx  包含查询文本和上下文的查询上下文对象。
     * @param dataSetId 数据集 ID，用于生成 S2SQL 语句。
     */
    private void tryParse(ChatQueryContext queryCtx, Long dataSetId) {
        LLMRequestService requestService = ContextUtils.getBean(LLMRequestService.class);
        LLMResponseService responseService = ContextUtils.getBean(LLMResponseService.class);
        int maxRetries = ContextUtils.getBean(LLMParserConfig.class).getRecallMaxRetries();

        LLMReq llmReq = requestService.getLlmReq(queryCtx, dataSetId);

        int currentRetry = 1;
        Map<String, LLMSqlResp> sqlRespMap = new HashMap<>();
        ParseResult parseResult = null;
        while (currentRetry <= maxRetries) {
            //log.info("当前重试轮次：{}，开始执行 runText2SQL", currentRetry);
            log.info("currentRetryRound:{}, start runText2SQL", currentRetry);
            try {
                LLMResp llmResp = requestService.runText2SQL(llmReq);
                if (Objects.nonNull(llmResp)) {
                    // 对 S2SQL 结果列表进行去重，并构建解析信息。
                    // deduplicate the S2SQL result list and build parserInfo
                    sqlRespMap = responseService.getDeduplicationSqlResp(currentRetry, llmResp);
                    if (MapUtils.isNotEmpty(sqlRespMap)) {
                        parseResult = ParseResult.builder().dataSetId(dataSetId).llmReq(llmReq)
                                .llmResp(llmResp).build();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("currentRetryRound:{}, runText2SQL failed", currentRetry, e);
            }
            ChatModelConfig chatModelConfig = llmReq.getChatAppConfig()
                    .get(OnePassSCSqlGenStrategy.APP_KEY).getChatModelConfig();
            Double temperature = chatModelConfig.getTemperature();
            if (temperature == 0) {
                // 报错时增加随机性，减少无效重试
                chatModelConfig.setTemperature(0.5);
            }
            currentRetry++;
        }
        if (MapUtils.isEmpty(sqlRespMap)) {
            return;
        }
        for (Entry<String, LLMSqlResp> entry : sqlRespMap.entrySet()) {
            String sql = entry.getKey();
            double sqlWeight = entry.getValue().getSqlWeight();
            responseService.addParseInfo(queryCtx, parseResult, sql, sqlWeight);
        }
    }
}
