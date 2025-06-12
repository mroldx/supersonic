package com.tencent.supersonic.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Text2DAX 示例集
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Text2DAXExemplar implements Serializable {

    public static final String PROPERTY_KEY = "dax_exemplar";

    private String question;

    private String sideInfo;

    private String dbSchema;

    private String sql;
}
