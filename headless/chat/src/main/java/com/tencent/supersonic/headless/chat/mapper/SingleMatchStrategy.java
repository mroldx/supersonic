package com.tencent.supersonic.headless.chat.mapper;

import com.tencent.supersonic.headless.api.pojo.response.S2Term;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import com.tencent.supersonic.headless.chat.knowledge.MapResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public abstract class SingleMatchStrategy<T extends MapResult> extends BaseMatchStrategy<T> {
    @Autowired
    protected MapperConfig mapperConfig;
    @Autowired
    protected MapperHelper mapperHelper;

    /**
     * 匹配
     * 获取分词结果的偏移量和长度映射（regOffsetToLength）。
     * 获取查询文本。
     * 初始化结果集 results 和任务列表 tasks。
     * 使用双重循环遍历文本，生成检测片段并创建任务。
     * 执行所有任务，返回匹配结果。
     * @param chatQueryContext
     * @param terms
     * @param detectDataSetIds
     * @return
     */
    public List<T> detect(ChatQueryContext chatQueryContext, List<S2Term> terms,
            Set<Long> detectDataSetIds) {
        Map<Integer, Integer> regOffsetToLength = mapperHelper.getRegOffsetToLength(terms);
        String text = chatQueryContext.getRequest().getQueryText();
        Set<T> results = ConcurrentHashMap.newKeySet();
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int startIndex = 0; startIndex <= text.length() - 1;) {
            for (int index = startIndex; index <= text.length();) {
                int offset = mapperHelper.getStepOffset(terms, startIndex);
                index = mapperHelper.getStepIndex(regOffsetToLength, index);
                if (index <= text.length()) {
                    String detectSegment = text.substring(startIndex, index).trim();
                    // 创建一个任务
                    // chatQueryContext: 聊天查询上下文。
                    //detectDataSetIds: 需要检测的数据集 ID 集合。
                    //detectSegment: 检测片段。
                    //offset: 偏移量。
                    //results: 结果集。
                    Callable<Void> task = createTask(chatQueryContext, detectDataSetIds,
                            detectSegment, offset, results);
                    tasks.add(task);
                }
            }
            startIndex = mapperHelper.getStepIndex(regOffsetToLength, startIndex);
        }
        executeTasks(tasks);
        return new ArrayList<>(results);
    }

    /**
     * 创建一个任务。
     * @param chatQueryContext 聊天查询上下文。
     * @param detectDataSetIds 需要检测的数据集 ID 集合。
     * @param detectSegment 检测片段。
     * @param offset 偏移量。
     * @param results 结果集。
     * @return 一个任务。
     */
    private Callable<Void> createTask(ChatQueryContext chatQueryContext, Set<Long> detectDataSetIds,
            String detectSegment, int offset, Set<T> results) {
        return () -> {
            List<T> oneRoundResults =
                    detectByStep(chatQueryContext, detectDataSetIds, detectSegment, offset);
            synchronized (results) {
                selectResultInOneRound(results, oneRoundResults);
            }
            return null;
        };
    }

    public abstract List<T> detectByStep(ChatQueryContext chatQueryContext,
            Set<Long> detectDataSetIds, String detectSegment, int offset);
}
