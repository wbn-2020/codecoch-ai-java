package com.codecoachai.system.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.system.domain.dto.SystemConfigQueryDTO;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.dto.SystemConfigStatusDTO;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;

public interface SystemConfigService {

    PageResult<SystemConfigVO> pageConfigs(SystemConfigQueryDTO query);

    SystemConfigVO createConfig(SystemConfigSaveDTO dto);

    SystemConfigVO getConfig(String keyOrId);

    SystemConfigVO updateConfigById(Long id, SystemConfigSaveDTO dto);

    SystemConfigVO updateConfigByKey(String configKey, SystemConfigSaveDTO dto);

    default SystemConfigVO updateConfig(String keyOrId, SystemConfigSaveDTO dto) {
        return isNumericIdentifier(keyOrId)
                ? updateConfigById(Long.valueOf(keyOrId), dto)
                : updateConfigByKey(keyOrId, dto);
    }

    SystemConfigVO updateConfigStatus(String keyOrId, SystemConfigStatusDTO dto);

    void deleteConfig(String keyOrId);

    AdminSystemOverviewVO overview();

    AdminDashboardOverviewVO dashboardOverview();

    private static boolean isNumericIdentifier(String value) {
        return value != null && value.matches("\\d+");
    }
}
