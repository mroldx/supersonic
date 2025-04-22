package com.tencent.supersonic.headless.api.pojo.enums;

/**
 * ChatWorkflowState 枚举类定义了聊天工作流的不同状态。
 * 该枚举类用于表示聊天工作流在执行过程中可能经历的各种阶段。
 */
public enum ChatWorkflowState {
    /**
     * 映射阶段，表示正在将输入数据映射到内部数据结构。
     */
    MAPPING,

    /**
     * 解析阶段，表示正在解析输入数据以提取关键信息。
     */
    PARSING,

    /**
     * S2SQL 校正阶段，表示正在对生成的 SQL 语句进行校正。
     */
    S2SQL_CORRECTING,

    /**
     * 翻译阶段，表示正在将解析后的信息翻译为 SQL 语句。
     */
    TRANSLATING,

    /**
     * 验证阶段，表示正在验证生成的 SQL 语句的正确性。
     */
    VALIDATING,

    /**
     * SQL 校正阶段，表示正在对 SQL 语句进行进一步的校正。
     */
    SQL_CORRECTING,

    /**
     * 处理阶段，表示正在执行 SQL 语句并处理结果。
     */
    PROCESSING,

    /**
     * 完成阶段，表示聊天工作流已成功完成。
     */
    FINISHED
}
