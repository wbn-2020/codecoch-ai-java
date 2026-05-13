package com.codecoachai.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.system.convert.SystemConfigConvert;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.entity.SystemConfig;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import com.codecoachai.system.mapper.SystemConfigMapper;
import com.codecoachai.system.service.SystemConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    @Override
    public List<SystemConfigVO> listConfigs() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                        .orderByDesc(SystemConfig::getUpdatedAt))
                .stream()
                .map(SystemConfigConvert::toVO)
                .toList();
    }

    @Override
    public SystemConfigVO createConfig(SystemConfigSaveDTO dto) {
        SystemConfig config = new SystemConfig();
        apply(config, dto);
        systemConfigMapper.insert(config);
        return SystemConfigConvert.toVO(config);
    }

    @Override
    public SystemConfigVO updateConfig(Long id, SystemConfigSaveDTO dto) {
        SystemConfig config = getConfig(id);
        apply(config, dto);
        systemConfigMapper.updateById(config);
        return SystemConfigConvert.toVO(config);
    }

    @Override
    public void deleteConfig(Long id) {
        systemConfigMapper.deleteById(id);
    }

    @Override
    public AdminSystemOverviewVO overview() {
        AdminSystemOverviewVO vo = new AdminSystemOverviewVO();
        vo.setUserCount(0L);
        vo.setQuestionCount(0L);
        vo.setResumeCount(0L);
        vo.setInterviewCount(0L);
        vo.setAiCallCount(0L);
        return vo;
    }

    private void apply(SystemConfig config, SystemConfigSaveDTO dto) {
        config.setConfigKey(dto.getConfigKey());
        config.setConfigValue(dto.getConfigValue());
        config.setValueType(dto.getValueType() == null ? "STRING" : dto.getValueType());
        config.setDescription(dto.getDescription());
        config.setStatus(dto.getStatus() == null ? CommonConstants.YES : dto.getStatus());
    }

    private SystemConfig getConfig(Long id) {
        SystemConfig config = systemConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "System config not found");
        }
        return config;
    }
}
