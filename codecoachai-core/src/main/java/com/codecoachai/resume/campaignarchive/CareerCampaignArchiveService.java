package com.codecoachai.resume.campaignarchive;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface CareerCampaignArchiveService {

    CareerCampaignArchiveModels.View create(Long campaignId, CareerCampaignArchiveModels.CreateRequest request);

    List<CareerCampaignArchiveModels.View> list(Long campaignId);

    CareerCampaignArchiveModels.View get(Long exportId);

    ResponseEntity<StreamingResponseBody> download(Long exportId);
}
