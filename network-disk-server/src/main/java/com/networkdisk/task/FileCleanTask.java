package com.networkdisk.task;

import com.networkdisk.entity.enums.FileDelFlagEnums;
import com.networkdisk.entity.po.FileInfo;
import com.networkdisk.entity.query.FileInfoQuery;
import com.networkdisk.service.FileInfoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 定时清理删除的文件
 */

@Component
public class FileCleanTask {

    @Resource
    private FileInfoService fileInfoService;

    @Scheduled(fixedDelay = 1000 * 60 * 3)
    public void execute() {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        fileInfoQuery.setQueryExpire(true);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(fileInfoQuery);
        // 根据用户id进行分组
        Map<String, List<FileInfo>> fileInfoMap = fileInfoList.stream()
                        .collect(Collectors.groupingBy(FileInfo::getUserId));

        for (Map.Entry<String, List<FileInfo>> entry : fileInfoMap.entrySet()) {

            List<String> fileIds = entry.getValue().stream().
                    map(FileInfo::getFileId).collect(Collectors.toList());
            fileInfoService.delFileBatch(entry.getKey(), String.join(",", fileIds), false);
        }
    }
}
