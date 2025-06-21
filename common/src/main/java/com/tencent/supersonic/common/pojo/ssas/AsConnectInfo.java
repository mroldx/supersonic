package com.tencent.supersonic.common.pojo.ssas;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: moli
 * @Email: 974751082@qq.com
 * @qq: 974751082
 * @Date: 2022/3/7 11:00
 */
@Data
public class AsConnectInfo implements Serializable {

    /**
     * 数据集名称/模型名称
     */
    private String dbName;

    /**
     * 数据集名称/模型名称
     */
    private String database;

    /**
     * 工作区名称
     */
    private String groupName;

    /**
     * 模型连接地址
     */
    private String connectUrl;

    /**
     * azure ad account
     */
    private String userId;

    /**
     * azure ad password
     */
    private String password;

    private String name;

    private String tableName;

    private String column;

    private String modelPermission;

    private String description;

    /**
     * dax筛选器
     */
    private String queryString;

    /**
     * 度量值名称
     */
    private String measureName;
}
