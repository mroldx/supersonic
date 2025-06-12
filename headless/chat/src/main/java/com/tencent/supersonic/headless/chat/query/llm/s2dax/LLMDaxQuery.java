package com.tencent.supersonic.headless.chat.query.llm.s2dax;

import com.tencent.supersonic.headless.api.pojo.DataSetSchema;
import com.tencent.supersonic.headless.api.pojo.DaxInfo;
import com.tencent.supersonic.headless.api.pojo.SqlInfo;
import com.tencent.supersonic.headless.chat.query.QueryManager;
import com.tencent.supersonic.headless.chat.query.llm.LLMSemanticQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLMDaxQuery
 */
@Slf4j
@Component
public class LLMDaxQuery extends LLMSemanticQuery {

    public static final String QUERY_MODE = "LLM_S2DAX";

    public LLMDaxQuery() {
        QueryManager.register(this);
    }

    @Override
    public String getQueryMode() {
        return QUERY_MODE;
    }

    @Override
    public void buildS2Sql(DataSetSchema dataSetSchema) {
        DaxInfo sqlInfo = parseInfo.getDaxInfo();
        sqlInfo.setCorrectedS2DAX(sqlInfo.getParsedS2DAX());
    }
}
