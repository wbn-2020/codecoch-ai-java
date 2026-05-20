package com.codecoachai.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.system.domain.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
