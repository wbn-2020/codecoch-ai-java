package com.codecoachai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.vo.AiModelHealthLogRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    List<AiModelHealthLogRow> selectModelHealthRows(@Param("models") List<AiModelConfig> models);
}
