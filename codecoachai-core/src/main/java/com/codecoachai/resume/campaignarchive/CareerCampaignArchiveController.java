package com.codecoachai.resume.campaignarchive;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class CareerCampaignArchiveController {

    private final CareerCampaignArchiveService archiveService;

    @OperationLog(module = "resume", action = "CREATE_CAMPAIGN_ARCHIVE_EXPORT",
            description = "生成求职周期档案导出", logResponse = false)
    @PostMapping("/career-campaigns/{campaignId}/archive-exports")
    public Result<CareerCampaignArchiveModels.View> create(
            @PathVariable Long campaignId,
            @Valid @RequestBody CareerCampaignArchiveModels.CreateRequest request) {
        return Result.success(archiveService.create(campaignId, request));
    }

    @GetMapping("/career-campaigns/{campaignId}/archive-exports")
    public Result<List<CareerCampaignArchiveModels.View>> list(@PathVariable Long campaignId) {
        return Result.success(archiveService.list(campaignId));
    }

    @GetMapping("/career-campaign-archive-exports/{exportId}")
    public Result<CareerCampaignArchiveModels.View> get(@PathVariable Long exportId) {
        return Result.success(archiveService.get(exportId));
    }

    @GetMapping("/career-campaign-archive-exports/{exportId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long exportId) {
        return archiveService.download(exportId);
    }
}
