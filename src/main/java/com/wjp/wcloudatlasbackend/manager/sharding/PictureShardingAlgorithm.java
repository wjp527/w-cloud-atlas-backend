package com.wjp.wcloudatlasbackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * 图片分片算法
 * @author wjp
 */
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        // 编写分表逻辑，返回实际要查询的表名
        // picture_0 物理表，picture 逻辑表

        Long spaceId = preciseShardingValue.getValue();
        // 获取逻辑表名
        String logicTableName = preciseShardingValue.getLogicTableName();

        // spaceId 为 null 表示查询所有图片
        if(spaceId == null) {
            return logicTableName;
        }

        // 根据 spaceId 动态生成表名
        String realTableName = "picture_" + spaceId;
        if(availableTargetNames.contains(realTableName)) {
            return realTableName;
        } else {
            return logicTableName;
        }

    }

    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Long> rangeShardingValue) {
        return new ArrayList<>();
    }

    @Override
    public Properties getProps() {
        return null;
    }

    @Override
    public void init(Properties properties) {

    }
}
