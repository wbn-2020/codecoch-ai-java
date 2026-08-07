package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.vo.IndustryTemplateVO;
import com.codecoachai.interview.service.IndustryTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IndustryTemplateController {

    private final IndustryTemplateService industryTemplateService;

    @GetMapping("/industry-templates")
    public Result<List<IndustryTemplateVO>> list() {
        return Result.success(industryTemplateService.userList());
    }

    @GetMapping("/industry-templates/{id}")
    public Result<IndustryTemplateVO> detail(@PathVariable Long id) {
        return Result.success(industryTemplateService.userDetail(id));
    }
}
