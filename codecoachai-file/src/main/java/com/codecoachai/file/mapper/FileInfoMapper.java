package com.codecoachai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.file.domain.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
