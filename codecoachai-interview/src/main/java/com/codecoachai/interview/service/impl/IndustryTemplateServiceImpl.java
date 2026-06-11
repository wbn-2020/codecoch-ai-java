package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.dto.IndustryTemplateCreateDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateQueryDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateUpdateDTO;
import com.codecoachai.interview.domain.entity.IndustryTemplate;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import com.codecoachai.interview.mapper.IndustryTemplateMapper;
import com.codecoachai.interview.service.IndustryTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class IndustryTemplateServiceImpl implements IndustryTemplateService {

    private final IndustryTemplateMapper industryTemplateMapper;

    @Override
    public List<IndustryTemplateVO> adminList(IndustryTemplateQueryDTO query) {
        LambdaQueryWrapper<IndustryTemplate> wrapper = new LambdaQueryWrapper<IndustryTemplate>()
                .eq(query != null && query.getEnabled() != null, IndustryTemplate::getEnabled, query == null ? null : query.getEnabled())
                .and(query != null && StringUtils.hasText(query.getKeyword()), w -> w
                        .like(IndustryTemplate::getIndustryCode, query.getKeyword())
                        .or()
                        .like(IndustryTemplate::getIndustryName, query.getKeyword())
                        .or()
                        .like(IndustryTemplate::getDescription, query.getKeyword()))
                .orderByAsc(IndustryTemplate::getSortOrder)
                .orderByDesc(IndustryTemplate::getUpdatedAt);
        return industryTemplateMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public IndustryTemplateVO adminDetail(Long id) {
        return toVO(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndustryTemplateVO create(IndustryTemplateCreateDTO dto) {
        validateCodeUnique(dto.getIndustryCode(), null);
        IndustryTemplate template = new IndustryTemplate();
        applyCreate(template, dto);
        industryTemplateMapper.insert(template);
        return toVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndustryTemplateVO update(Long id, IndustryTemplateUpdateDTO dto) {
        IndustryTemplate template = requireTemplate(id);
        validateCodeUnique(dto.getIndustryCode(), id);
        applyUpdate(template, dto);
        industryTemplateMapper.updateById(template);
        return toVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        IndustryTemplate template = requireTemplate(id);
        template.setEnabled(CommonConstants.YES);
        industryTemplateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        IndustryTemplate template = requireTemplate(id);
        template.setEnabled(CommonConstants.NO);
        industryTemplateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        industryTemplateMapper.deleteById(id);
    }

    @Override
    public List<IndustryTemplateVO> userList() {
        return industryTemplateMapper.selectList(enabledWrapper())
                .stream()
                .map(this::toUserVO)
                .toList();
    }

    @Override
    public IndustryTemplateVO userDetail(Long id) {
        return toUserVO(getEnabledTemplate(id));
    }

    @Override
    public IndustryTemplate getEnabledTemplate(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择行业模板");
        }
        IndustryTemplate template = industryTemplateMapper.selectOne(enabledWrapper().eq(IndustryTemplate::getId, id).last("limit 1"));
        if (template == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行业模板不存在或已停用");
        }
        return template;
    }

    private LambdaQueryWrapper<IndustryTemplate> enabledWrapper() {
        return new LambdaQueryWrapper<IndustryTemplate>()
                .eq(IndustryTemplate::getEnabled, CommonConstants.YES)
                .orderByAsc(IndustryTemplate::getSortOrder)
                .orderByDesc(IndustryTemplate::getUpdatedAt);
    }

    private IndustryTemplate requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择行业模板");
        }
        IndustryTemplate template = industryTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行业模板不存在");
        }
        return template;
    }

    private void validateCodeUnique(String industryCode, Long excludeId) {
        IndustryTemplate existing = industryTemplateMapper.selectOne(new LambdaQueryWrapper<IndustryTemplate>()
                .eq(IndustryTemplate::getIndustryCode, industryCode)
                .ne(excludeId != null, IndustryTemplate::getId, excludeId)
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Industry code already exists");
        }
    }

    private void applyCreate(IndustryTemplate template, IndustryTemplateCreateDTO dto) {
        template.setIndustryCode(dto.getIndustryCode());
        template.setIndustryName(dto.getIndustryName());
        template.setDescription(dto.getDescription());
        template.setTargetPositions(dto.getTargetPositions());
        template.setCoreBusinessScenarios(dto.getCoreBusinessScenarios());
        template.setKeyTechnicalPoints(dto.getKeyTechnicalPoints());
        template.setCommonQuestionDirections(dto.getCommonQuestionDirections());
        template.setRiskPoints(dto.getRiskPoints());
        template.setPromptContext(dto.getPromptContext());
        template.setEnabled(dto.getEnabled() == null ? CommonConstants.YES : dto.getEnabled());
        template.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    private void applyUpdate(IndustryTemplate template, IndustryTemplateUpdateDTO dto) {
        template.setIndustryCode(dto.getIndustryCode());
        template.setIndustryName(dto.getIndustryName());
        template.setDescription(dto.getDescription());
        template.setTargetPositions(dto.getTargetPositions());
        template.setCoreBusinessScenarios(dto.getCoreBusinessScenarios());
        template.setKeyTechnicalPoints(dto.getKeyTechnicalPoints());
        template.setCommonQuestionDirections(dto.getCommonQuestionDirections());
        template.setRiskPoints(dto.getRiskPoints());
        template.setPromptContext(dto.getPromptContext());
        template.setEnabled(dto.getEnabled() == null ? CommonConstants.YES : dto.getEnabled());
        template.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    private IndustryTemplateVO toVO(IndustryTemplate template) {
        if (template == null) {
            return null;
        }
        IndustryTemplateVO vo = new IndustryTemplateVO();
        vo.setIndustryTemplateId(template.getId());
        vo.setIndustryCode(template.getIndustryCode());
        vo.setIndustryName(template.getIndustryName());
        vo.setDescription(template.getDescription());
        vo.setTargetPositions(template.getTargetPositions());
        vo.setCoreBusinessScenarios(template.getCoreBusinessScenarios());
        vo.setKeyTechnicalPoints(template.getKeyTechnicalPoints());
        vo.setCommonQuestionDirections(template.getCommonQuestionDirections());
        vo.setRiskPoints(template.getRiskPoints());
        vo.setPromptContext(template.getPromptContext());
        vo.setEnabled(template.getEnabled());
        vo.setSortOrder(template.getSortOrder());
        vo.setCreatedAt(template.getCreatedAt());
        vo.setUpdatedAt(template.getUpdatedAt());
        return vo;
    }

    private IndustryTemplateVO toUserVO(IndustryTemplate template) {
        IndustryTemplateVO vo = toVO(template);
        if (vo != null) {
            vo.setPromptContext(null);
        }
        return vo;
    }
}
