package com.tencent.supersonic.headless.chat.parser.llm;

import com.amazonaws.services.bedrockagent.model.Agent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.pojo.Pair;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.util.DateUtils;
import com.tencent.supersonic.common.util.JsonUtil;
import com.tencent.supersonic.headless.api.pojo.*;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import com.tencent.supersonic.headless.chat.parser.ParserConfig;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMResp;
import com.tencent.supersonic.headless.chat.utils.ComponentFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.tencent.supersonic.headless.chat.parser.ParserConfig.*;

@Slf4j
@Service
public class LLMRequestService {

    @Autowired
    private ParserConfig parserConfig;

    private static final String SYS_EXEMPLAR_FILE = "s2-daxExemplar.json";

    private TypeReference<List<Text2SQLExemplar>> valueTypeRef =
            new TypeReference<List<Text2SQLExemplar>>() {};

    private final ObjectMapper objectMapper = JsonUtil.INSTANCE.getObjectMapper();

    /**
     * 获取查询上下文中的数据集 ID。 通过 DataSetResolver 解析查询上下文和请求中的数据集 ID 列表，返回匹配的数据集 ID。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @return 匹配的数据集 ID，如果未找到则返回 null。
     */
    public Long getDataSetId(ChatQueryContext queryCtx) {
        DataSetResolver dataSetResolver = ComponentFactory.getModelResolver();
        return dataSetResolver.resolve(queryCtx, queryCtx.getRequest().getDataSetIds());
    }

    /**
     * 构建 LLM 请求对象。 根据查询上下文和数据集 ID，提取查询文本、指标、维度、数据库信息、分区时间、主键等，构建 LLMReq 对象。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 构建的 LLMReq 对象。
     */
    public LLMReq getLlmReq(ChatQueryContext queryCtx, Long dataSetId) {
        Map<Long, String> dataSetIdToName = queryCtx.getSemanticSchema().getDataSetIdToName();
        String queryText = queryCtx.getRequest().getQueryText();
        // 构建 LLM 模式（LLMSchema）
        LLMReq.LLMSchema llmSchema = new LLMReq.LLMSchema();
        int fieldCntThreshold =
                Integer.parseInt(parserConfig.getParameterValue(PARSER_FIELDS_COUNT_THRESHOLD));
        if (queryCtx.getMapInfo().getMatchedElements(dataSetId).size() <= fieldCntThreshold) {
            // 如果匹配的字段数量小于阈值，则使用完整的指标和维度
            llmSchema.setMetrics(queryCtx.getSemanticSchema().getMetrics());
            llmSchema.setDimensions(queryCtx.getSemanticSchema().getDimensions());
        } else {
            // 否则，使用映射的指标和维度
            llmSchema.setMetrics(getMappedMetrics(queryCtx, dataSetId));
            llmSchema.setDimensions(getMappedDimensions(queryCtx, dataSetId));
        }
        // 构建 LLM 请求对象
        LLMReq llmReq = new LLMReq();
        llmReq.setQueryText(queryText);
        llmReq.setSchema(llmSchema);
        Pair<String, String> databaseInfo = getDatabaseType(queryCtx, dataSetId);
        llmSchema.setDatabaseType(databaseInfo.first);
        llmSchema.setDatabaseVersion(databaseInfo.second);
        // 判断是否切换dax解析
        if (llmSchema.getDatabaseType().equals("SSAS")
                || llmSchema.getDatabaseType().equals("PowerBI-SemanticModel")) {
            llmSchema.setSSAS(true);
        }
        llmSchema.setDataSetId(dataSetId);
        llmSchema.setDataSetName(dataSetIdToName.get(dataSetId));
        llmSchema.setPartitionTime(getPartitionTime(queryCtx, dataSetId));
        llmSchema.setPrimaryKey(getPrimaryKey(queryCtx, dataSetId));
        // 如果启用了链接值功能，则添加映射的值
        boolean linkingValueEnabled =
                Boolean.parseBoolean(parserConfig.getParameterValue(PARSER_LINKING_VALUE_ENABLE));
        if (linkingValueEnabled) {
            llmSchema.setValues(getMappedValues(queryCtx, dataSetId));
        }
        // 设置当前日期、术语、SQL 生成类型、聊天应用配置和动态示例
        llmReq.setCurrentDate(DateUtils.getBeforeDate(0));
        llmReq.setTerms(getMappedTerms(queryCtx, dataSetId));
        llmReq.setSqlGenType(LLMReq.SqlGenType.valueOf(determineSqlGenTypeFromChatAppConfig(
                queryCtx.getRequest().getChatAppConfig(), llmSchema.isSSAS())));
        llmReq.setChatAppConfig(queryCtx.getRequest().getChatAppConfig());
        if (llmSchema.isSSAS()) {
            llmReq.setDynamicExemplars(loadSysExemplars());
        } else {
            llmReq.setDynamicExemplars(loadExemplars());
        }

        return llmReq;
    }

    /**
     * 根据 chatAppConfig 确定 SQLGenType。
     *
     * @param chatAppConfig 聊天应用配置对象。
     * @param isDax 是否要切换dax解析
     * @return SQLGenType 的字符串表示。
     */
    private String determineSqlGenTypeFromChatAppConfig(Map<String, ChatApp> chatAppConfig,
            boolean isDax) {
        if (chatAppConfig != null && chatAppConfig.containsKey(OnePassSCDaxGenStrategy.APP_KEY)) {
            ChatApp chatApp = chatAppConfig.get(OnePassSCDaxGenStrategy.APP_KEY);
            if (chatApp.isEnable() && isDax) {
                return parserConfig.getParameterValue(PARSER_STRATEGY_DAX_TYPE);
            }
        }

        // 默认值
        return parserConfig.getParameterValue(PARSER_STRATEGY_TYPE);
    }

    public List<Text2SQLExemplar> loadSysExemplars() {
        try {
            ClassPathResource resource = new ClassPathResource(SYS_EXEMPLAR_FILE);
            InputStream inputStream = resource.getInputStream();

            return objectMapper.readValue(inputStream, valueTypeRef);
        } catch (Exception e) {
            log.error("Failed to load system exemplars", e);
        }
        return Collections.emptyList();
    }

    public List<Text2SQLExemplar> loadExemplars() {
        try {
            ClassPathResource resource = new ClassPathResource("s2-exemplar.json");
            InputStream inputStream = resource.getInputStream();

            return objectMapper.readValue(inputStream, valueTypeRef);
        } catch (Exception e) {
            log.error("Failed to load system exemplars", e);
        }
        return Collections.emptyList();
    }

    /**
     * 执行 Text2SQL 转换。 根据 LLMReq 对象，使用指定的 SQL 生成策略生成 SQL 语句，并返回 LLMResp 对象。
     *
     * @param llmReq LLM 请求对象，包含查询文本、模式等信息。
     * @return 生成的 LLMResp 对象，包含 SQL 查询结果。
     */
    public LLMResp runText2SQL(LLMReq llmReq) {
        SqlGenStrategy sqlGenStrategy = SqlGenStrategyFactory.get(llmReq.getSqlGenType());
        String dataSet = llmReq.getSchema().getDataSetName();
        LLMResp result = sqlGenStrategy.generate(llmReq);
        result.setQuery(llmReq.getQueryText());
        result.setDataSet(dataSet);
        return result;
    }

    /**
     * 获取映射的术语列表。 从查询上下文中提取与数据集 ID 匹配的术语元素，并转换为 LLMReq.Term 对象列表。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 术语列表，如果未找到则返回空列表。
     */
    protected List<LLMReq.Term> getMappedTerms(ChatQueryContext queryCtx, Long dataSetId) {
        List<SchemaElementMatch> matchedElements =
                queryCtx.getMapInfo().getMatchedElements(dataSetId);
        if (CollectionUtils.isEmpty(matchedElements)) {
            return new ArrayList<>();
        }
        return matchedElements.stream().filter(schemaElementMatch -> {
            SchemaElementType elementType = schemaElementMatch.getElement().getType();
            return SchemaElementType.TERM.equals(elementType);
        }).map(schemaElementMatch -> {
            LLMReq.Term term = new LLMReq.Term();
            term.setName(schemaElementMatch.getElement().getName());
            term.setDescription(schemaElementMatch.getElement().getDescription());
            term.setAlias(schemaElementMatch.getElement().getAlias());
            return term;
        }).collect(Collectors.toList());
    }


    /**
     * 获取映射的值列表。 从查询上下文中提取与数据集 ID 匹配的值元素，并转换为 LLMReq.ElementValue 对象列表。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 值列表，如果未找到则返回空列表。
     */
    protected List<LLMReq.ElementValue> getMappedValues(@NotNull ChatQueryContext queryCtx,
            Long dataSetId) {
        List<SchemaElementMatch> matchedElements =
                queryCtx.getMapInfo().getMatchedElements(dataSetId);
        if (CollectionUtils.isEmpty(matchedElements)) {
            return new ArrayList<>();
        }
        Set<LLMReq.ElementValue> valueMatches = matchedElements.stream()
                .filter(elementMatch -> !elementMatch.isInherited()).filter(schemaElementMatch -> {
                    SchemaElementType type = schemaElementMatch.getElement().getType();
                    return SchemaElementType.VALUE.equals(type)
                            || SchemaElementType.ID.equals(type);
                }).map(elementMatch -> {
                    LLMReq.ElementValue elementValue = new LLMReq.ElementValue();
                    elementValue.setFieldName(elementMatch.getElement().getName());
                    elementValue.setFieldValue(elementMatch.getWord());
                    return elementValue;
                }).collect(Collectors.toSet());
        return new ArrayList<>(valueMatches);
    }

    /**
     * 获取映射的指标列表。 从查询上下文中提取与数据集 ID 匹配的指标元素，并转换为 SchemaElement 对象列表。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 指标列表，如果未找到则返回空列表。
     */
    protected List<SchemaElement> getMappedMetrics(@NotNull ChatQueryContext queryCtx,
            Long dataSetId) {
        List<SchemaElementMatch> matchedElements =
                queryCtx.getMapInfo().getMatchedElements(dataSetId);
        if (CollectionUtils.isEmpty(matchedElements)) {
            return Collections.emptyList();
        }
        return matchedElements.stream().filter(schemaElementMatch -> {
            SchemaElementType elementType = schemaElementMatch.getElement().getType();
            return SchemaElementType.METRIC.equals(elementType);
        }).map(SchemaElementMatch::getElement).collect(Collectors.toList());
    }

    /**
     * 获取映射的维度列表。 从查询上下文中提取与数据集 ID 匹配的维度元素，并转换为 SchemaElement 对象列表。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 维度列表，如果未找到则返回空列表。
     */
    protected List<SchemaElement> getMappedDimensions(@NotNull ChatQueryContext queryCtx,
            Long dataSetId) {

        List<SchemaElementMatch> matchedElements =
                queryCtx.getMapInfo().getMatchedElements(dataSetId);
        List<SchemaElement> dimensionElements = matchedElements.stream().filter(
                element -> SchemaElementType.DIMENSION.equals(element.getElement().getType()))
                .map(SchemaElementMatch::getElement).collect(Collectors.toList());

        return new ArrayList<>(dimensionElements);
    }

    /**
     * 获取分区时间字段。 从查询上下文中提取与数据集 ID 匹配的分区时间字段。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 分区时间字段，如果未找到则返回 null。
     */
    protected SchemaElement getPartitionTime(@NotNull ChatQueryContext queryCtx, Long dataSetId) {
        SemanticSchema semanticSchema = queryCtx.getSemanticSchema();
        if (semanticSchema == null || semanticSchema.getDataSetSchemaMap() == null) {
            return null;
        }
        Map<Long, DataSetSchema> dataSetSchemaMap = semanticSchema.getDataSetSchemaMap();
        DataSetSchema dataSetSchema = dataSetSchemaMap.get(dataSetId);
        return dataSetSchema.getPartitionDimension();
    }

    /**
     * 获取主键字段。 从查询上下文中提取与数据集 ID 匹配的主键字段。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 主键字段，如果未找到则返回 null。
     */
    protected SchemaElement getPrimaryKey(@NotNull ChatQueryContext queryCtx, Long dataSetId) {
        SemanticSchema semanticSchema = queryCtx.getSemanticSchema();
        if (semanticSchema == null || semanticSchema.getDataSetSchemaMap() == null) {
            return null;
        }
        Map<Long, DataSetSchema> dataSetSchemaMap = semanticSchema.getDataSetSchemaMap();
        DataSetSchema dataSetSchema = dataSetSchemaMap.get(dataSetId);
        return dataSetSchema.getPrimaryKey();
    }

    /**
     * 获取数据库类型和版本。 从查询上下文中提取与数据集 ID 匹配的数据库类型和版本。
     *
     * @param queryCtx 查询上下文对象，包含查询的上下文信息。
     * @param dataSetId 数据集 ID。
     * @return 数据库类型和版本的键值对，如果未找到则返回 null。
     */
    protected Pair<String, String> getDatabaseType(@NotNull ChatQueryContext queryCtx,
            Long dataSetId) {
        SemanticSchema semanticSchema = queryCtx.getSemanticSchema();
        if (semanticSchema == null || semanticSchema.getDataSetSchemaMap() == null) {
            return null;
        }
        Map<Long, DataSetSchema> dataSetSchemaMap = semanticSchema.getDataSetSchemaMap();
        DataSetSchema dataSetSchema = dataSetSchemaMap.get(dataSetId);
        return new Pair(dataSetSchema.getDatabaseType(), dataSetSchema.getDatabaseVersion());
    }
}
