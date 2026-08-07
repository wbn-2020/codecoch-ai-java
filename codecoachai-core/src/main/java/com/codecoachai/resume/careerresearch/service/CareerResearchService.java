package com.codecoachai.resume.careerresearch.service;

import com.codecoachai.resume.careerresearch.dto.CareerResearchSnapshotGenerateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceCreateDTO;
import com.codecoachai.resume.careerresearch.dto.CareerResearchSourceVersionCreateDTO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSnapshotVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVO;
import com.codecoachai.resume.careerresearch.vo.CareerResearchSourceVersionVO;
import java.util.List;

public interface CareerResearchService {
    List<CareerResearchSourceVO> listSources(Long applicationId);
    CareerResearchSourceVO createSource(Long applicationId, CareerResearchSourceCreateDTO request);
    CareerResearchSourceVersionVO addSourceVersion(Long sourceId,
                                                   CareerResearchSourceVersionCreateDTO request);
    void deactivateSource(Long sourceId);
    CareerResearchSnapshotVO generateSnapshot(Long applicationId,
                                              CareerResearchSnapshotGenerateDTO request);
    CareerResearchSnapshotVO latestSnapshot(Long applicationId);
    CareerResearchSnapshotVO getSnapshot(Long snapshotId);
}
