package com.tencent.supersonic.headless.core.executor;

import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.pojo.ssas.AsConnectInfo;
import com.tencent.supersonic.common.pojo.ssas.DaxResultInfo;
import com.tencent.supersonic.common.util.SsasXmlaClientUtils;
import com.tencent.supersonic.headless.api.pojo.enums.SemanticType;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.api.pojo.response.SemanticQueryResp;
import com.tencent.supersonic.headless.core.pojo.QueryStatement;
import com.tencent.supersonic.headless.core.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

@Component("Olap4jExecutor")
@Slf4j
public class Olap4jExecutor implements QueryExecutor {

    private static final String aas_restapi = "http://192.168.0.115:5000";

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
            // 多行存储 k v
            List<Map<String, Object>> resultInfos = null;
            if (!database.getUrl().contains("http")) {
                AsConnectInfo asConnectInfo = new AsConnectInfo();
                asConnectInfo.setConnectUrl(database.getUrl());
                asConnectInfo.setDatabase(database.getName());
                asConnectInfo.setUserId(database.getUsername());
                asConnectInfo.setPassword(database.getPassword());
                asConnectInfo.setDbName(database.getName());
                asConnectInfo.setGroupName(database.getDatabase());
                asConnectInfo.setQueryString(sql);
                resultInfos = sqlUtils.executeDaxByCloud(asConnectInfo, aas_restapi);
            } else {
                resultInfos = sqlUtils.getDaxResult(database.getUrl(), sql, database.getName());
            }

            List<QueryColumn> queryColumns = new ArrayList<>();
            boolean isNull = CollectionUtils.isEmpty(resultInfos);
            if (!isNull) {
                resultInfos = convertQueryResult(resultInfos);
                //获取list里面map最多的key
                int maxKeyLength = resultInfos.stream().map(Map::size).max(Integer::compareTo).orElse(0);
                for (String key : resultInfos.get(maxKeyLength-1).keySet()) {
                    QueryColumn queryColumn = new QueryColumn();
                    queryColumn.setName(key);
                    queryColumn.setBizName(key);
                    queryColumn.setType("String");
                    queryColumns.add(queryColumn);
                }
                //给最后一列的showType设置为number
                queryColumns.get(queryColumns.size() - 1).setShowType(SemanticType.NUMBER.name());
                queryResultWithColumns.setColumns(queryColumns);
            } else {
                QueryColumn queryColumn = new QueryColumn();
                queryColumn.setName("查询结果");
                queryColumn.setBizName("查询结果");
                queryColumn.setType("String");
                queryColumns.add(queryColumn);
                resultInfos = new ArrayList<>();
                Map<String, Object> resultInfo = new HashMap<>();
                resultInfo.put("查询结果", "0");
                resultInfos.add(resultInfo);
            }
            log.info("查询结果条数为：{},开始截取", resultInfos.size());
            //截取100条
            resultInfos = resultInfos.stream().limit(100).collect(Collectors.toList());
            queryResultWithColumns.setResultList(resultInfos);
            queryResultWithColumns.setSql(sql);
        } catch (Exception e) {
            log.error("queryInternal with error ", e);
            queryResultWithColumns.setErrorMsg(e.getMessage());
        }
        return queryResultWithColumns;
    }

    @NotNull
    private List<Map<String, Object>> convertQueryResult(List<Map<String, Object>> resultInfos) {
        resultInfos = resultInfos.stream().map(resultInfo -> {
            Map<String, Object> newResultInfo = new LinkedHashMap<>();
            for (String key : resultInfo.keySet()) {
                // 判断第一个字符是否[开头
                if (key.charAt(0) != '[') {
                    newResultInfo.put(convertColumn(key), resultInfo.get(key));
                } else {
                    newResultInfo.put(convertMeasure(key), resultInfo.get(key));
                }
            }
            return newResultInfo;
        }).collect(Collectors.toList());
        return resultInfos;
    }

    /**
     * 转换column xxx[xxxx]-> xxx.xxxx
     */
    private String convertColumn(String column) {
        return column.replaceAll("\\[", ".").replaceAll("]", "");
    }

    /**
     * 转换度量值列 [xxxx] -> xxxx
     */
    private String convertMeasure(String measure) {
        return measure.replaceAll("\\[", "").replaceAll("]", "");
    }
}
