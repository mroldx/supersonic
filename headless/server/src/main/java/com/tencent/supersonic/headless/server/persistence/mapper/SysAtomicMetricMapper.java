package com.tencent.supersonic.headless.server.persistence.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.supersonic.headless.server.persistence.dataobject.poweropp.SysAtomicMetric;
import org.apache.ibatis.annotations.Mapper;

@DS("slave")
@Mapper
public interface SysAtomicMetricMapper extends BaseMapper<SysAtomicMetric> {
}
