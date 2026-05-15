package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.dto.IndustryTemplateCreateDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateQueryDTO;
import com.codecoachai.interview.domain.dto.IndustryTemplateUpdateDTO;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import com.codecoachai.interview.service.IndustryTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminIndustryTemplateController {

    private final IndustryTemplateService industryTemplateService;

    @GetMapping("/admin/industry-templates")
    public Result<List<IndustryTemplateVO>> list(IndustryTemplateQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(industryTemplateService.adminList(query));
    }

    @GetMapping("/admin/industry-templates/{id}")
    public Result<IndustryTemplateVO> detail(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(industryTemplateService.adminDetail(id));
    }

    @PostMapping("/admin/industry-templates")
    public Result<IndustryTemplateVO> create(@Valid @RequestBody IndustryTemplateCreateDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(industryTemplateService.create(dto));
    }

    @PutMapping("/admin/industry-templates/{id}")
    public Result<IndustryTemplateVO> update(@PathVariable Long id,
                                             @Valid @RequestBody IndustryTemplateUpdateDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(industryTemplateService.update(id, dto));
    }

    @PostMapping("/admin/industry-templates/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        industryTemplateService.enable(id);
        return Result.success();
    }

    @PostMapping("/admin/industry-templates/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        industryTemplateService.disable(id);
        return Result.success();
    }

    @DeleteMapping("/admin/industry-templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        industryTemplateService.delete(id);
        return Result.success();
    }
}
