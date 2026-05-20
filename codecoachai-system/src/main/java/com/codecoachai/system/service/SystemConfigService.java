package com.codecoachai.system.service;

import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.dto.SystemConfigStatusDTO;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import java.util.List;

public interface SystemConfigService {

    List<SystemConfigVO> listConfigs();

    SystemConfigVO createConfig(SystemConfigSaveDTO dto);

    SystemConfigVO getConfig(String keyOrId);

    SystemConfigVO updateConfig(String keyOrId, SystemConfigSaveDTO dto);

    SystemConfigVO updateConfigStatus(String keyOrId, SystemConfigStatusDTO dto);

    void deleteConfig(String keyOrId);

    AdminSystemOverviewVO overview();

    AdminDashboardOverviewVO dashboardOverview();
}
