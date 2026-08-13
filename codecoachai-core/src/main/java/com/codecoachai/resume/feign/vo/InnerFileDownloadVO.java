package com.codecoachai.resume.feign.vo;

import lombok.Data;

@Data
public class InnerFileDownloadVO {

    private Long fileId;
    private String originalFilename;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private byte[] content;
}
