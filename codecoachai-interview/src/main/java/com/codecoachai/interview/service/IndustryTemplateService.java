package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.IndustryTemplateCreateDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateQueryDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateUpdateDTO;
import com.codecoachai.interview.domain.entity.IndustryTemplate;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import java.util.List;

public interface IndustryTemplateService {

    List<IndustryTemplateVO> adminList(IndustryTemplateQueryDTO query);

    IndustryTemplateVO adminDetail(Long id);

    IndustryTemplateVO create(IndustryTemplateCreateDTO dto);

    IndustryTemplateVO update(Long id, IndustryTemplateUpdateDTO dto);

    void enable(Long id);

    void disable(Long id);

    void delete(Long id);

    List<IndustryTemplateVO> userList();

    IndustryTemplateVO userDetail(Long id);

    IndustryTemplate getEnabledTemplate(Long id);
}
