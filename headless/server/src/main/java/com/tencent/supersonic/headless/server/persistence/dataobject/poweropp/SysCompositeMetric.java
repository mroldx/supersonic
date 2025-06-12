package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 *
 * @TableName sys_composite_metric
 */
@TableName(value = "sys_composite_metric")
@Data
public class SysCompositeMetric {
    /**
     * 复合指标ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 复合指标名称
     */
    private String name;

    /**
     * 复合指标描述
     */
    private String description;

    /**
     * 数据负责人
     */
    private String owner;

    /**
     * 复合指标类型，1：表达式，2：环比增长率，3：同比增长率
     */
    private String metricType;

    /**
     * 指标目录ID
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long catalogId;

    /**
     * 数据格式
     */
    private String dataFormat;

    /**
     * 指标编码
     */
    private String code;

    /**
     * 是否采集
     */
    @TableField(exist = false)
    private String hasCollect;

    /**
     * 发布状态（0：已发布，1：未发布）
     */
    private String publish;

    /**
     * 版本号
     */
    private String version;

    /**
     * 审批责任人
     */
    private String approver;

    /**
     * 英文名称
     */
    private String enName;

    /**
     * 是否核心指标
     */
    private String hasCore;

    /**
     * 指标定义
     */
    private String definition;

    /**
     * 计算公式
     */
    private String calculationFormula;

    /**
     * 统计口径
     */
    private String caliberDescription;

    /**
     * 指标管理的部门
     */
    private String managementDept;

    /**
     * 计量单位
     */
    private String unit;

    private String securityLevel;

    /**
     * 数据有效期
     */
    private Date dataValidity;

    /**
     * 是否自动续期 0是，1否
     */
    private String autoReNew;

    @TableField(exist = false)
    private String indicatorType = "composite";
}
