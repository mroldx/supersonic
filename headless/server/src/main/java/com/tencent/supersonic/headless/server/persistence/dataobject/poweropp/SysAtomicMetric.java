package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 *
 * @TableName sys_atomic_metric
 */
@TableName(value = "sys_atomic_metric")
@Data
public class SysAtomicMetric {
    /**
     * 指标ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 原子指标名称
     */
    private String name;

    /**
     * 指标描述
     */
    private String description;

    /**
     * 安全等级(1:机密，2：秘密，3：内部公开，4：完全公开）
     */
    private String securityLevel;

    /**
     * 模型ID
     */
    private Long semanticModelId;

    /**
     * 度量值名称
     */
    private String measureName;

    /**
     * 发布状态（0：已发布，1：未发布）
     */
    private String publish;

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
     * 数据负责人
     */
    private String owner;

    /**
     * 数据有效期
     */
    private Date dataValidity;

    /**
     * 是否自动续期
     */
    private String autoReNew;

    /**
     * 指标编码
     */
    private String code;

    /**
     * 模型名称
     */
    @TableField(exist = false)
    private String semanticModel;

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
     * 计量单位
     */
    private String unit;
}
