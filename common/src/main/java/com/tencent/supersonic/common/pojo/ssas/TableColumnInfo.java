package com.tencent.supersonic.common.pojo.ssas;

import lombok.Data;

@Data
public class TableColumnInfo {

    /**
     * 列名
     */
    private String columnName;
    /**
     * 数据类型
     */
    private String dataType;
    /**
     * 列描述
     */
    private String comment;
}
