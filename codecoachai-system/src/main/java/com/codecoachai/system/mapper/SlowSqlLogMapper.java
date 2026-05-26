package com.codecoachai.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.system.domain.entity.SlowSqlLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SlowSqlLogMapper extends BaseMapper<SlowSqlLog> {
}
