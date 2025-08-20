package com.tencent.supersonic.headless.chat.corrector;

import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/** Perform DAX corrections on the "Filter" format section in S2DAX. */
@Slf4j
public class S2DAXFilterCorrector extends BaseSemanticCorrector {

    private static final Pattern INT_LITERAL =
            Pattern.compile("(?<=[=!><]=?\\s*)[\"'](\\d+)[\"']");

    public static String reFormat(String dax) {
        return INT_LITERAL.matcher(dax).replaceAll("$1");
    }

    @Override
    public void doCorrect(ChatQueryContext chatQueryContext, SemanticParseInfo semanticParseInfo) {
        String correctS2SQL = semanticParseInfo.getSqlInfo().getCorrectedS2SQL();
        try {
            if(correctS2SQL.contains("EVALUATE")){
                semanticParseInfo.getSqlInfo().setCorrectedS2SQL(reFormat(correctS2SQL));
                log.info("S2DAXFilterCorrector parseCondExpression: {}", correctS2SQL);
            }
        } catch (Exception e) {
            log.error("S2DAXFilterCorrector parseCondExpression", e);
        }
    }

    public static void main(String[] args) {
        String a = reFormat("EVALUATE SUMMARIZECOLUMNS('date_dim'[d_year], FILTER(VALUES('date_dim'[d_year]), 'date_dim'[d_year] = \"2023\"), \"销售收入\", [销售收入])");
        System.out.println(a);
    }
}
