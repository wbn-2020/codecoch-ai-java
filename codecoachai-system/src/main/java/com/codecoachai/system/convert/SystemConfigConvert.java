package com.codecoachai.system.convert;

import com.codecoachai.system.domain.entity.SystemConfig;
import com.codecoachai.system.domain.vo.SystemConfigVO;

public final class SystemConfigConvert {

    private SystemConfigConvert() {
    }

    public static SystemConfigVO toVO(SystemConfig config) {
        SystemConfigVO vo = new SystemConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(config.getConfigValue());
        vo.setValueType(config.getValueType());
        vo.setDescription(config.getDescription());
        vo.setStatus(config.getStatus());
        return vo;
    }
}
