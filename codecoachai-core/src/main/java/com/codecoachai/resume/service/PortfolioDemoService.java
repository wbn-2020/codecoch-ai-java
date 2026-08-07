package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.PortfolioDemoStatusVO;
import com.codecoachai.resume.domain.vo.PortfolioDemoStorylineVO;

public interface PortfolioDemoService {

    PortfolioDemoStatusVO status();

    PortfolioDemoStatusVO load();

    PortfolioDemoStatusVO reset();

    PortfolioDemoStorylineVO storyline();
}
