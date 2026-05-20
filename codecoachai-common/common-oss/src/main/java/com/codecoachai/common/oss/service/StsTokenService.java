package com.codecoachai.common.oss.service;

import com.codecoachai.common.oss.domain.StsTokenVO;

/**
 * STS 临时凭证服务（前端直传 OSS 用）。
 */
public interface StsTokenService {

    /**
     * 生成允许写入指定目录前缀的临时凭证。
     *
     * @param dirPrefix 目录前缀（如 "resume/123/2026/05/"），凭证只能写此前缀下的对象
     * @return 临时凭证
     */
    StsTokenVO generate(String dirPrefix);
}
