package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 *
 * @TableName sys_derived_metric
 */
@TableName(value = "sys_derived_metric")
@Data
public class SysDerivedMetric {
    /**
     * 派生指标ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 派生指标名称
     */
    private String name;

    /**
     * 所属指标目录ID
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long catalogId;

    /**
     * 所关联的原子指标ID
     */
    private Long atomicId;

    /**
     * 派生指标描述
     */
    private String description;

    /**
     * 发布状态（0：已发布，1：未发布）
     */
    private String publish;

    /**
     * 数据格式
     */
    private String dataFormat;

    /**
     * 数据负责人
     */
    private String owner;

    /**
     * 派生指标编码
     */
    private String code;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

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
     * 指标管理部门
     */
    private String managementDept;

    /**
     * 数据有效期
     */
    private Date dataValidity;

    /**
     * 是否自动续期
     */
    private String autoReNew;

    private String securityLevel;
}
