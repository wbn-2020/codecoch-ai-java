package com.codecoachai.ai.agent.campaigncockpit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CampaignCockpitEvidenceContractTest {

    @Test
    void readsResumeEvidenceFieldNamesWithoutDroppingActionFacts() throws Exception {
        EvidenceEnvelope evidence = new ObjectMapper().readValue("""
                {
                  "userId": 7,
                  "campaignId": 12,
                  "campaignTitle": "秋招周期",
                  "campaignStatus": "ACTIVE",
                  "campaign": {
                    "id": 12,
                    "name": "秋招周期",
                    "goal": "稳定推进后端岗位",
                    "status": "ACTIVE"
                  },
                  "applications": [{
                    "id": 31,
                    "applicationId": 31,
                    "interviewPrepMissing": false,
                    "interviewReviewMissing": true,
                    "materialCoverageLow": true,
                    "researchCoverageLow": false,
                    "sourceHash": "resume-source-hash",
                    "sources": [{
                      "sourceType": "JOB_APPLICATION",
                      "sourceId": 31,
                      "sourceHash": "resume-source-hash"
                    }]
                  }]
                }
                """, EvidenceEnvelope.class);

        assertEquals("秋招周期", evidence.getCampaign().getName());
        assertEquals("稳定推进后端岗位", evidence.getCampaign().getGoal());
        var application = evidence.getApplications().get(0);
        assertFalse(Boolean.TRUE.equals(application.getInterviewPrepMissing()));
        assertTrue(Boolean.TRUE.equals(application.getInterviewReviewMissing()));
        assertTrue(Boolean.TRUE.equals(application.getMaterialCoverageLow()));
        assertEquals("resume-source-hash", application.getSourceHash());
        assertEquals(1, application.getEvidenceRefs().size());
        assertEquals("JOB_APPLICATION",
                application.getEvidenceRefs().get(0).getSourceType());
    }
}
