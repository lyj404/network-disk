package com.networkdisk.entity.dto;

import lombok.Data;

@Data
public class DownloadFileDto {
    private String downloadCode;
    private String fileName;
    private String filePath;
}
