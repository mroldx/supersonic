package com.tencent.supersonic.headless.core.executor;

import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.util.SsasXmlaClientUtils;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.api.pojo.response.SemanticQueryResp;
import com.tencent.supersonic.headless.core.pojo.QueryStatement;
import com.tencent.supersonic.headless.core.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("Olap4jExecutor")
@Slf4j
public class Olap4jExecutor implements QueryExecutor {
    @Override
    public boolean accept(QueryStatement queryStatement) {
        return queryStatement.getIsS2DAX();
    }

    @Override
    public SemanticQueryResp execute(QueryStatement queryStatement) {
        SsasXmlaClientUtils sqlUtils = new SsasXmlaClientUtils();
        String sql = StringUtils.normalizeSpace(queryStatement.getSql());
        log.info("executing DAX: {}", sql);
        DatabaseResp database = queryStatement.getOntology().getDatabase();
        SemanticQueryResp queryResultWithColumns = new SemanticQueryResp();
        try {
            Map<String, Object> data =
                    sqlUtils.getDaxResult(database.getUrl(), sql, database.getName());

            List<QueryColumn> queryColumns = new ArrayList<>();
            for (String key : data.keySet()) {
                QueryColumn queryColumn = new QueryColumn();
                queryColumn.setName(key);
                queryColumn.setType("String");
                queryColumns.add(queryColumn);
            }
            queryResultWithColumns.setColumns(queryColumns);
            List<Map<String, Object>> objects = new ArrayList<>();
            objects.add(data);
            queryResultWithColumns.setResultList(objects);
            queryResultWithColumns.setSql(sql);
        } catch (Exception e) {
            log.error("queryInternal with error ", e);
            queryResultWithColumns.setErrorMsg(e.getMessage());
        }
        return queryResultWithColumns;
    }
}
