package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.PortfolioRehearsalSessionSaveDTO;
import com.codecoachai.resume.domain.vo.PortfolioRehearsalSessionVO;

public interface PortfolioRehearsalSessionService {

    /**
     * Load the current user's rehearsal session, or a default empty session when none exists.
     */
    PortfolioRehearsalSessionVO current();

    /**
     * Upsert the current user's rehearsal session with the latest route/node/timer progress.
     */
    PortfolioRehearsalSessionVO save(PortfolioRehearsalSessionSaveDTO dto);

    /**
     * Reset the current user's rehearsal session back to the default empty state.
     */
    PortfolioRehearsalSessionVO reset();
}
