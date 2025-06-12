package com.tencent.supersonic.common.pojo.enums;

public enum Text2DAXType {
    ONLY_RULE, LLM_OR_RULE, NONE;

    public boolean enableLLM() {
        return this.equals(LLM_OR_RULE);
    }
}
