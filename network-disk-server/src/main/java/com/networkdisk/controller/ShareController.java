package com.networkdisk.controller;

import com.networkdisk.annotation.GlobalInterceptor;
import com.networkdisk.annotation.VerifyParam;
import com.networkdisk.controller.basecontroller.BaseController;
import com.networkdisk.entity.dto.SessionWebUserDto;
import com.networkdisk.entity.po.FileShare;
import com.networkdisk.entity.query.FileShareQuery;
import com.networkdisk.entity.vo.PaginationResultVO;
import com.networkdisk.entity.vo.ResponseVO;
import com.networkdisk.service.FileShareService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController("shareController")
@RequestMapping("/share")
public class ShareController extends BaseController {

    @Resource
    private FileShareService fileShareService;


    @PostMapping("/loadShareList")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO loadShareList(HttpSession session, FileShareQuery query) {
        query.setOrderBy("share_time desc");
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        query.setUserId(userDto.getUserId());
        query.setQueryFileName(true);
        PaginationResultVO resultVO = this.fileShareService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }

    @PostMapping("/shareFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO shareFile(HttpSession session,
                                @VerifyParam(required = true) String fileId,
                                @VerifyParam(required = true) Integer validType,
                                String code) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = new FileShare();
        share.setFileId(fileId);
        share.setValidType(validType);
        share.setCode(code);
        share.setUserId(userDto.getUserId());
        fileShareService.saveShare(share);
        return getSuccessResponseVO(share);
    }

    @PostMapping("/cancelShare")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO cancelShare(HttpSession session,
                                  @VerifyParam(required = true) String shareIds) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        fileShareService.deleteFileShareBatch(shareIds.split(","), userDto.getUserId());
        return getSuccessResponseVO(null);
    }
}
