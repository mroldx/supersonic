package com.tencent.supersonic.headless.chat.parser;

import com.tencent.supersonic.common.pojo.DimensionConstants;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.chat.parser.llm.PromptHelper;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PromptHelperTest {
    @Test
    public void testBuildDaxSchemaStr_PartitionTimeFieldMultiFormat() {
        // 构造partitionTime，extInfo的value为表名.字段名
        SchemaElement partitionTime = new SchemaElement();
        Map<String, Object> extInfo = new HashMap<>();
        extInfo.put(DimensionConstants.DIMENSION_TIME_FORMAT_YYYY_MM_DD, "D_日期表.Date");
        extInfo.put(DimensionConstants.DIMENSION_TIME_FORMAT_YYYY, "D_日期表.年");
        extInfo.put(DimensionConstants.DIMENSION_TIME_FORMAT_YYYY_MM, "D_日期表.月");
        partitionTime.setExtInfo(extInfo);

        // 构造schema
        LLMReq.LLMSchema schema = new LLMReq.LLMSchema();
        schema.setDataSetName("D_日期表");
        schema.setPartitionTime(partitionTime);
        schema.setDimensions(new ArrayList<>());
        schema.setMetrics(new ArrayList<>());
        schema.setValues(new ArrayList<>());

        // 构造llmReq
        LLMReq llmReq = new LLMReq();
        llmReq.setSchema(schema);

        // 调用方法
        PromptHelper helper = new PromptHelper();
        String result = helper.buildDaxSchemaStr(llmReq);

        // 断言PartitionTimeField部分
        assertTrue(result.contains(
                "PartitionTimeField=[<D_日期表.Date FORMAT 'yyyy-MM-dd'>,<D_日期表.年 FORMAT 'yyyy'>,<D_日期表.月 FORMAT 'yyyy-MM'>]"),
                "PartitionTimeField格式不正确，实际结果：" + result);
    }
}
