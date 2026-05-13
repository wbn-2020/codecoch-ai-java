package com.codecoachai.system.service;

import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import java.util.List;

public interface SystemConfigService {

    List<SystemConfigVO> listConfigs();

    SystemConfigVO createConfig(SystemConfigSaveDTO dto);

    SystemConfigVO updateConfig(Long id, SystemConfigSaveDTO dto);

    void deleteConfig(Long id);

    AdminSystemOverviewVO overview();
}
