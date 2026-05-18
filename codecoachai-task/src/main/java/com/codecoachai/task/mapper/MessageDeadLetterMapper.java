package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageDeadLetterMapper extends BaseMapper<MessageDeadLetter> {
}
