package com.tencent.supersonic.headless.server.task;

import javax.annotation.PreDestroy;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tencent.supersonic.common.config.EmbeddingConfig;
import com.tencent.supersonic.common.pojo.DataItem;
import com.tencent.supersonic.common.pojo.enums.PublishEnum;
import com.tencent.supersonic.common.pojo.enums.StatusEnum;
import com.tencent.supersonic.common.service.EmbeddingService;
import com.tencent.supersonic.headless.api.pojo.request.DatabaseReq;
import com.tencent.supersonic.headless.core.pojo.ConnectInfo;
import com.tencent.supersonic.headless.server.persistence.dataobject.DatabaseDO;
import com.tencent.supersonic.headless.server.persistence.dataobject.DimensionDO;
import com.tencent.supersonic.headless.server.persistence.dataobject.MetricDO;
import com.tencent.supersonic.headless.server.persistence.dataobject.ModelDO;
import com.tencent.supersonic.headless.server.persistence.dataobject.poweropp.SysAtomicMetric;
import com.tencent.supersonic.headless.server.persistence.dataobject.poweropp.SysIndicatorModifier;
import com.tencent.supersonic.headless.server.persistence.dataobject.poweropp.SysSemanticModel;
import com.tencent.supersonic.headless.server.persistence.dataobject.poweropp.SysSemanticModelCredentials;
import com.tencent.supersonic.headless.server.persistence.mapper.*;
import com.tencent.supersonic.headless.server.service.DimensionService;
import com.tencent.supersonic.headless.server.service.MetricService;
import com.tencent.supersonic.headless.server.utils.DatabaseConverter;
import dev.langchain4j.inmemory.spring.InMemoryEmbeddingStoreFactory;
import dev.langchain4j.store.embedding.EmbeddingStoreFactory;
import dev.langchain4j.store.embedding.EmbeddingStoreFactoryProvider;
import dev.langchain4j.store.embedding.TextSegmentConvert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.extension.toolkit.Db.updateById;

/**
 * 转换PowerOpp指标平台数据进supernsonic
 */
@Component
@Slf4j
@Order(3)
public class PowerOppConvertTask implements CommandLineRunner {

    @Autowired
    private SysSemanticModelCredentialsMapper credentialsMapper;

    @Autowired
    private SysSemanticModelMapper semanticModelMapper;

    @Autowired
    private SysIndicatorModifierMapper modifierMapper;

    @Autowired
    private DimensionDOMapper dimensionDOMapper;

    @Autowired
    private DatabaseDOMapper databaseDOMapper;

    @Autowired
    private ModelDOMapper modelDOMapper;

    @Autowired
    private SysAtomicMetricMapper atomicMetricMapper;

    @Autowired
    private MetricDOMapper metricDOMapper;

    // @Scheduled(cron = "${s2.inMemoryEmbeddingStore.persist.cron:0 0 * * * ?}")
    public void executePersistFileTask() {
        // embeddingStorePersistFile();
    }

    public void reload() {
        // 从语义模型凭据导入进数据库链接
        List<SysSemanticModelCredentials> credentials = credentialsMapper.selectList(null);

        for (SysSemanticModelCredentials credential : credentials) {

            List<DatabaseDO> databaseDOList =
                    databaseDOMapper.selectList(new LambdaQueryWrapper<DatabaseDO>()
                            .in(DatabaseDO::getType, "SSAS", "PowerBI-SemanticModel"));

            List<SysSemanticModel> sysSemanticModels =
                    this.semanticModelMapper.selectList(new LambdaQueryWrapper<SysSemanticModel>()
                            .eq(SysSemanticModel::getCredentialId, credential.getId()));

            for (SysSemanticModel sysSemanticModel : sysSemanticModels) {

                if (sysSemanticModel == null) {
                    continue;
                }

                Optional<DatabaseDO> first = databaseDOList.stream().filter(databaseDO -> databaseDO
                        .getName().equalsIgnoreCase(sysSemanticModel.getName())).findFirst();

                Long databaseId;

                if (first.isPresent()) {
                    DatabaseDO convert =
                            convert(credential, first.get(), sysSemanticModel.getServer());
                    convert.setUpdatedAt(new Date());
                    convert.setUpdatedBy("PowerOppConvertTask");
                    updateById(convert);
                    databaseId = first.get().getId();
                } else {
                    DatabaseDO databaseReq =
                            convert(credential, new DatabaseDO(), sysSemanticModel.getServer());
                    databaseReq.setName(sysSemanticModel.getName());
                    databaseReq.setDescription(sysSemanticModel.getDescription());
                    databaseReq.setCreatedBy("PowerOppConvertTask");
                    databaseReq.setCreatedAt(new Date());
                    databaseReq.setUpdatedBy("PowerOppConvertTask");
                    databaseReq.setUpdatedAt(new Date());
                    databaseReq.setVersion("2016");
                    databaseReq.setType(credential.getType());
                    databaseDOMapper.insert(databaseReq);
                    databaseId = databaseReq.getId();
                }

                // 在指定的主题域插入模型对象
                Long domainId = 5L;


                ModelDO modelDO = this.modelDOMapper.selectOne(
                        new LambdaQueryWrapper<ModelDO>().eq(ModelDO::getDomainId, domainId)
                                .eq(ModelDO::getName, sysSemanticModel.getName()));
                if (modelDO == null) {
                    ModelDO modelDO1 = new ModelDO();
                    modelDO1.setDomainId(domainId);
                    modelDO1.setName(sysSemanticModel.getName());
                    modelDO1.setBizName(sysSemanticModel.getAlias());
                    modelDO1.setDescription(sysSemanticModel.getDescription());
                    modelDO1.setStatus(StatusEnum.ONLINE.getCode());
                    modelDO1.setCreatedAt(new Date());
                    modelDO1.setCreatedBy("PowerOppConvertTask");
                    modelDO1.setUpdatedBy("PowerOppConvertTask");
                    modelDO1.setUpdatedAt(new Date());
                    modelDO1.setDrillDownDimensions("[]");
                    modelDO1.setDatabaseId(databaseId);
                    modelDO1.setModelDetail("""
                            {
                                "dimensions": [],
                                "fields": [],
                                "identifiers": [],
                                "measures": [],
                                "queryType": "table_query",
                                "sqlQuery": "",
                                "sqlVariables": [],
                                "tableQuery": ""
                            }""");
                    modelDOMapper.insert(modelDO1);
                    modelDO = modelDO1;
                }

                Long modelId = modelDO.getId();

                // 现有模型的指标
                List<MetricDO> metricDOS = this.metricDOMapper.selectList(
                        new LambdaQueryWrapper<MetricDO>().eq(MetricDO::getModelId, modelId));

                List<String> metricName = metricDOS.stream().map(MetricDO::getName).toList();

                // 源模型指标
                List<SysAtomicMetric> atomicMetrics =
                        atomicMetricMapper.selectList(new LambdaQueryWrapper<SysAtomicMetric>()
                                .eq(SysAtomicMetric::getSemanticModelId, sysSemanticModel.getId()));

                // 筛选出现有模型没有的
                for (SysAtomicMetric atomicMetric : atomicMetrics) {
                    if (!metricName.contains(atomicMetric.getName())) {
                        MetricDO metricDO = new MetricDO();
                        metricDO.setModelId(modelId);
                        metricDO.setName(atomicMetric.getName());
                        metricDO.setBizName(atomicMetric.getMeasureName());
                        metricDO.setDescription(atomicMetric.getDescription());
                        metricDO.setAlias(atomicMetric.getMeasureName());
                        metricDO.setStatus(StatusEnum.ONLINE.getCode());
                        metricDO.setSensitiveLevel(0);
                        metricDO.setType("ATOMIC");
                        metricDO.setTypeParams("{\"expr\":\"" + atomicMetric.getMeasureName()
                                + "\",\"measures\":[{\"agg\":\"sum\",\"bizName\":\""
                                + atomicMetric.getEnName() + "\"}]}");
                        metricDO.setDefineType("MEASURE");
                        metricDO.setCreatedAt(new Date());
                        metricDO.setCreatedBy("PowerOppConvertTask");
                        metricDO.setUpdatedBy("PowerOppConvertTask");
                        metricDO.setUpdatedAt(new Date());
                        metricDO.setIsPublish(StatusEnum.ONLINE.getCode());
                        metricDOMapper.insert(metricDO);
                    }
                }

                // 现有模型维度
                List<DimensionDO> dimensionDOS = this.dimensionDOMapper.selectList(
                        new LambdaQueryWrapper<DimensionDO>().eq(DimensionDO::getModelId, modelId));

                List<String> dimenList = dimensionDOS.stream().map(DimensionDO::getName).toList();

                // 现有模型时间维度
                List<DimensionDO> timeDimensions =
                        this.dimensionDOMapper.selectList(new LambdaQueryWrapper<DimensionDO>()
                                .eq(DimensionDO::getType, "partition_time")
                                .eq(DimensionDO::getModelId, modelId));

                List<String> timeDimensionList =
                        timeDimensions.stream().map(DimensionDO::getName).toList();

                // 源模型维度
                List<SysIndicatorModifier> modifiers =
                        modifierMapper.selectList(new LambdaQueryWrapper<SysIndicatorModifier>().eq(
                                SysIndicatorModifier::getSemanticModel, sysSemanticModel.getId()));

                for (SysIndicatorModifier modifier : modifiers) {
                    if (!dimenList.contains(modifier.getName())) {
                        DimensionDO dimensionDO = new DimensionDO();
                        dimensionDO.setModelId(modelId);
                        dimensionDO.setName(modifier.getName());
                        dimensionDO.setBizName(modifier.getReferencesField());
                        dimensionDO.setStatus(StatusEnum.ONLINE.getCode());
                        dimensionDO.setSensitiveLevel(0);
                        dimensionDO.setType("categorical");
                        dimensionDO.setTypeParams(
                                "{\"expr\":\"" + modifier.getReferencesField() + "\"}");
                        dimensionDO.setCreatedAt(new Date());
                        dimensionDO.setCreatedBy("PowerOppConvertTask");
                        dimensionDO.setUpdatedBy("PowerOppConvertTask");
                        dimensionDO.setUpdatedAt(new Date());
                        dimensionDO.setSemanticType("CATEGORY");
                        dimensionDO.setDescription(modifier.getName());
                        dimensionDO.setExpr("");
                        dimensionDO.setIsTag(0);
                        dimensionDOMapper.insert(dimensionDO);
                    }
                }

                // 源时间维度
                String time = sysSemanticModel.getEntity();
                String[] split = time.split("\\.");
                String tableName = split[0];

                if (!timeDimensionList.contains(tableName)) {
                    DimensionDO dimensionDO = new DimensionDO();
                    dimensionDO.setModelId(modelId);
                    dimensionDO.setName(tableName);
                    dimensionDO.setBizName(time);
                    dimensionDO.setStatus(StatusEnum.ONLINE.getCode());
                    dimensionDO.setSensitiveLevel(0);
                    dimensionDO.setType("partition_time");
                    dimensionDO.setTypeParams("{\"expr\":\"" + time + "\"}");
                    dimensionDO.setCreatedAt(new Date());
                    dimensionDO.setCreatedBy("PowerOppConvertTask");
                    dimensionDO.setUpdatedBy("PowerOppConvertTask");
                    dimensionDO.setUpdatedAt(new Date());
                    dimensionDO.setSemanticType("DATE");
                    dimensionDO.setDescription(time);
                    dimensionDO.setAlias("");
                    dimensionDO.setExpr("{\"time_format\":\"yyyy-MM-dd\"}");
                    dimensionDO.setIsTag(0);
                    dimensionDOMapper.insert(dimensionDO);
                }

                //获取树形维度，判断树形维度是否已建立过
//                List<DimensionDO> treeDimensions =

            }
        }
    }

    public static DatabaseDO convert(SysSemanticModelCredentials credentials, DatabaseDO databaseDO,
            String database) {
        ConnectInfo connectInfo = getConnectInfo(credentials, database);
        databaseDO.setConfig(JSONObject.toJSONString(connectInfo));
        return databaseDO;
    }

    public static ConnectInfo getConnectInfo(SysSemanticModelCredentials credentials,
            String database) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUserName(credentials.getUsername());
        connectInfo.setPassword(credentials.getPassword());
        connectInfo.setUrl(credentials.getUrl());
        connectInfo.setDatabase(database);
        return connectInfo;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            reload();
        } catch (Exception e) {
            log.error("initMetaEmbedding error", e);
        }
    }
}
