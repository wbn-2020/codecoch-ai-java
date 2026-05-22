package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.dto.SkillProfileGenerateDTO;
import com.codecoachai.resume.domain.dto.SkillProfileQueryDTO;
import com.codecoachai.resume.domain.dto.SkillProfileRefreshDTO;
import com.codecoachai.resume.domain.vo.SkillProfileDetailVO;
import com.codecoachai.resume.domain.vo.SkillProfileGenerateVO;
import com.codecoachai.resume.domain.vo.SkillProfileListVO;
import com.codecoachai.resume.domain.vo.SkillProfileOverviewVO;
import com.codecoachai.resume.service.SkillProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/skill-profiles")
public class SkillProfileController {

    private final SkillProfileService skillProfileService;

    @PostMapping("/generate")
    public Result<SkillProfileGenerateVO> generate(@Valid @RequestBody SkillProfileGenerateDTO dto) {
        return Result.success(skillProfileService.generate(dto));
    }

    @GetMapping("/by-job-target/{targetJobId}")
    public Result<SkillProfileDetailVO> getByTargetJob(@PathVariable Long targetJobId) {
        return Result.success(skillProfileService.getByTargetJob(targetJobId));
    }

    @GetMapping("/overview")
    public Result<SkillProfileOverviewVO> overview(@RequestParam(required = false) Long targetJobId) {
        return Result.success(skillProfileService.getOverview(targetJobId));
    }

    @GetMapping("/latest")
    public Result<SkillProfileListVO> latest() {
        SkillProfileQueryDTO query = new SkillProfileQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(1L);
        PageResult<SkillProfileListVO> page = skillProfileService.listProfiles(query);
        SkillProfileListVO latest = page.getRecords() == null || page.getRecords().isEmpty()
                ? null
                : page.getRecords().get(0);
        return Result.success(latest);
    }

    @GetMapping
    public Result<PageResult<SkillProfileListVO>> list(@ModelAttribute SkillProfileQueryDTO query) {
        return Result.success(skillProfileService.listProfiles(query));
    }

    @PostMapping("/refresh")
    public Result<SkillProfileGenerateVO> refresh(@RequestBody SkillProfileRefreshDTO dto) {
        return Result.success(skillProfileService.refresh(dto));
    }
}
