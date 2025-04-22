package com.tencent.supersonic.chat.server.util;

import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.server.pojo.ParseContext;
import com.tencent.supersonic.common.pojo.enums.Text2SQLType;
import com.tencent.supersonic.common.util.BeanMapper;
import com.tencent.supersonic.headless.api.pojo.request.QueryNLReq;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class QueryReqConverter {

    public static QueryNLReq buildQueryNLReq(ParseContext parseContext) {
        QueryNLReq queryNLReq = new QueryNLReq();
        BeanMapper.mapper(parseContext.getRequest(), queryNLReq);
        queryNLReq.setText2SQLType(
                parseContext.enableLLM() ? Text2SQLType.LLM_OR_RULE : Text2SQLType.ONLY_RULE);
        queryNLReq.setDataSetIds(getDataSetIds(parseContext));
        // agent的chatAppConfig含有大模型的配置，提示词等等信息存在agent表里
        queryNLReq.setChatAppConfig(parseContext.getAgent().getChatAppConfig());
        queryNLReq.setSelectedParseInfo(parseContext.getRequest().getSelectedParse());
        return queryNLReq;
    }

    private static Set<Long> getDataSetIds(ParseContext parseContext) {
        ChatParseReq chatParseReq = parseContext.getRequest();
        //解析agent里面的toolConfig jsonString里面的datasetId
        Set<Long> dataSetIds = parseContext.getAgent().getDataSetIds();
        Long requestDataSetId = chatParseReq.getDataSetId();

        if (Objects.nonNull(requestDataSetId)) {
            if (CollectionUtils.isEmpty(dataSetIds)) {
                return Collections.singleton(requestDataSetId);
            }
            dataSetIds.removeIf(dataSetId -> !dataSetId.equals(requestDataSetId));
        }
        return dataSetIds;
    }
}
