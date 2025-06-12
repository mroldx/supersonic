package com.tencent.supersonic.headless.server.pojo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SSASParametersBuilder implements DbParametersBuilder {

    @Override
    public List<DatabaseParameter> build() {
        List<DatabaseParameter> databaseParameters = new ArrayList<>();
        DatabaseParameter host = new DatabaseParameter();
        host.setComment("链接");
        host.setName("url");
        host.setPlaceholder("请输入XMLA端点");
        databaseParameters.add(host);
        return databaseParameters;
    }
}
