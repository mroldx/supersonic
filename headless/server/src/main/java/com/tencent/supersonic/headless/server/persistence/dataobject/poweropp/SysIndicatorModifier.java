package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 *
 * @TableName sys_indicator_modifier
 */
@TableName(value = "sys_indicator_modifier")
@Data
public class SysIndicatorModifier {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 修饰词名称
     */
    private String name;

    /**
     * 引用数据字段按表.列进行区分
     */
    private String referencesField;

    /**
     * 所属语义模型
     */
    private String semanticModel;
}
