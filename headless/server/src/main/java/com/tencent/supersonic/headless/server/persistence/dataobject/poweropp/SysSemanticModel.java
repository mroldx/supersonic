package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 *
 * @TableName sys_semantic_model
 */
@TableName(value = "sys_semantic_model")
@Data
public class SysSemanticModel {
    /**
     * 语义模型ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * 数据管理部门
     */
    private String dataManagerDept;

    /**
     * 数据负责人
     */
    private String dataOwner;

    /**
     * 所属目录
     */
    private String catalog;

    /**
     * 目录名称
     */
    @TableField(exist = false)
    private String catalogName;

    /**
     * 封面
     */
    private String cover;

    /**
     * 缩略图
     */
    private String thumbnail;

    /**
     * 语义模型的数据源凭据ID
     */
    private String credentialId;

    /**
     * 此模型是否认证0：是，1：否
     */
    private String authStatus;

    /**
     * 是否被指标引用 0：是，1：否
     */
    private String applyStatus;

    /**
     * 语义模型服务器
     */
    private String server;

    /**
     * 模型别名
     */
    private String alias;

    /**
     * 模型事实表
     */
    private String subject;

    /**
     * 模型时间维度表 表.列
     */
    private String entity;

    /**
     * 日期关联
     */
    private String dateAssign;
}
