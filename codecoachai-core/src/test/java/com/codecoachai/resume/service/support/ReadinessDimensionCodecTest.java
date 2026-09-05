package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec.ValidationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadinessDimensionCodecTest {

    private ReadinessDimensionCodec codec;

    @BeforeEach
    void setUp() {
        codec = new ReadinessDimensionCodec(new ObjectMapper());
    }

    @Test
    void roundTripsValidFiveDimensionPayload() {
        String json = codec.encode(validDimensions());

        var decoded = codec.decode(json, ReadinessDimensionCodec.SCHEMA_VERSION);

        assertTrue(decoded.valid());
        assertEquals(ValidationStatus.VALID, decoded.status());
        assertEquals(5, decoded.dimensions().size());
    }

    @Test
    void distinguishesEmptyMalformedStructuralAndUnsupportedPayloads() throws Exception {
        assertEquals(ValidationStatus.EMPTY,
                codec.decode(" ", ReadinessDimensionCodec.SCHEMA_VERSION).status());
        assertEquals(ValidationStatus.INVALID_JSON,
                codec.decode("[", ReadinessDimensionCodec.SCHEMA_VERSION).status());
        assertEquals(ValidationStatus.INVALID_STRUCTURE,
                codec.decode("{}", ReadinessDimensionCodec.SCHEMA_VERSION).status());
        assertEquals(ValidationStatus.INVALID_STRUCTURE,
                codec.decode("\"[]\"", ReadinessDimensionCodec.SCHEMA_VERSION).status());
        assertEquals(ValidationStatus.UNSUPPORTED_SCHEMA,
                codec.decode("[]", "readiness-dimensions-v99").status());

        List<JobReadinessSnapshotVO.DimensionScore> missingField = validDimensions();
        missingField.get(0).setScore(null);
        assertEquals(ValidationStatus.INVALID_STRUCTURE,
                codec.decode(new ObjectMapper().writeValueAsString(missingField),
                        ReadinessDimensionCodec.SCHEMA_VERSION).status());
    }

    @Test
    void rejectsDuplicateOrIncompleteDimensionsBeforeWrite() {
        List<JobReadinessSnapshotVO.DimensionScore> incomplete =
                new ArrayList<>(validDimensions().subList(0, 4));
        assertThrows(BusinessException.class, () -> codec.encode(incomplete));

        List<JobReadinessSnapshotVO.DimensionScore> duplicate = validDimensions();
        duplicate.get(4).setDimension("RESUME");
        assertThrows(BusinessException.class, () -> codec.encode(duplicate));
    }

    @Test
    void normalizesLegacyDefaultsAndSingleEncodedLegacyJson() throws Exception {
        List<JobReadinessSnapshotVO.DimensionScore> legacy = validDimensions();
        legacy.forEach(item -> {
            item.setConfidenceLevel(null);
            item.setFallback(null);
            item.setSampleInsufficient(null);
        });
        ObjectMapper mapper = new ObjectMapper();
        String doubleEncoded = mapper.writeValueAsString(mapper.writeValueAsString(legacy));

        var decoded = codec.decode(doubleEncoded, ReadinessDimensionCodec.LEGACY_SCHEMA_VERSION);

        assertTrue(decoded.valid());
        assertEquals(ValidationStatus.VALID_LEGACY, decoded.status());
        assertTrue(decoded.dimensions().stream().allMatch(item -> item.getFallback() != null));
        assertTrue(decoded.dimensions().stream().allMatch(item -> item.getSampleInsufficient() != null));
        assertEquals("HIGH", decoded.dimensions().get(0).getConfidenceLevel());
        assertEquals("MEDIUM", decoded.dimensions().get(1).getConfidenceLevel());
    }

    static List<JobReadinessSnapshotVO.DimensionScore> validDimensions() {
        return List.of(
                dimension("RESUME", 80, 2),
                dimension("PROJECT_EVIDENCE", 75, 1),
                dimension("KNOWLEDGE", 70, 3),
                dimension("INTERVIEW", 65, 1),
                dimension("APPLICATION", 60, 1));
    }

    private static JobReadinessSnapshotVO.DimensionScore dimension(
            String name, int score, int sampleCount) {
        JobReadinessSnapshotVO.DimensionScore item =
                new JobReadinessSnapshotVO.DimensionScore();
        item.setDimension(name);
        item.setScore(score);
        item.setSampleCount(sampleCount);
        item.setConfidenceLevel(sampleCount > 1 ? "HIGH" : "MEDIUM");
        item.setFallback(false);
        item.setSampleInsufficient(false);
        return item;
    }
}
