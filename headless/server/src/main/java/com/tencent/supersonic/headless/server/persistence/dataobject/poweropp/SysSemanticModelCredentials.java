package com.tencent.supersonic.headless.server.persistence.dataobject.poweropp;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 *
 * @TableName sys_semantic_model_credentials
 */
@TableName(value = "sys_semantic_model_credentials")
@Data
public class SysSemanticModelCredentials {
    /**
     * 语义模型数据源凭据ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源名称
     */
    @Length(max = 250, message = "数据源名称长度不能超过250个字符")
    private String name;

    /**
     * 数据源类型 SSAS，PowerBI-SemanticModel，AzureAS
     */
    private String type;

    /**
     * 连接地址含端口
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    @TableField(insertStrategy = FieldStrategy.NOT_EMPTY, updateStrategy = FieldStrategy.NOT_EMPTY,
            whereStrategy = FieldStrategy.NOT_EMPTY)
    private String password;
}
