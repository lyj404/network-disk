package com.networkdisk.service.impl;


import com.networkdisk.utils.RedisComponent;
import com.networkdisk.config.AppConfig;
import com.networkdisk.entity.constants.Constants;
import com.networkdisk.entity.dto.SysSettingsDto;
import com.networkdisk.entity.po.EmailCode;
import com.networkdisk.entity.po.UserInfo;
import com.networkdisk.entity.query.EmailCodeQuery;
import com.networkdisk.entity.query.UserInfoQuery;
import com.networkdisk.exception.BusinessException;
import com.networkdisk.mappers.EmailCodeMapper;
import com.networkdisk.mappers.UserInfoMapper;
import com.networkdisk.service.EmailCodeService;
import com.networkdisk.utils.RedisUtils;
import com.networkdisk.utils.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.Date;


/**
 * 邮箱验证码 业务接口实现
 */
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final Logger logger = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    @Resource
    private JavaMailSender javaMailSender;
    @Resource
    private EmailCodeMapper<EmailCode, EmailCodeQuery> emailCodeMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private AppConfig appConfig;
    @Resource
    private RedisComponent redisComponent;

    @Resource
    private RedisUtils redisUtils;


    /**
     * 真正发送邮件验证码
     * @param toEmail 发送到德邮箱
     * @param code 需要发送的验证码
     */
    private void sendEmailCode(String toEmail, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            //邮件发件人
            helper.setFrom(appConfig.getSendUserName());
            //邮件收件人 1或多个
            helper.setTo(toEmail);

            SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();

            //邮件主题
            helper.setSubject(sysSettingsDto.getRegisterEmailTitle());
            //邮件内容
            helper.setText(String.format(sysSettingsDto.getRegisterEmailContent(), code));
            //邮件发送时间
            helper.setSentDate(new Date());
            //发送
            javaMailSender.send(message);
        } catch (Exception e) {
            logger.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败");
        }
    }

    /**
     * 发送邮箱验证码的前置和后置工作
     * @param toEmail 发送的邮箱地址
     * @param type  0:注册  1:找回密码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendEmailCode(String toEmail, Integer type) {
        //如果是注册，校验邮箱是否已存在
        if (type == Constants.REGISTER_ZERO) {
            UserInfo userInfo = userInfoMapper.selectByEmail(toEmail);
            if (null != userInfo) {
                throw new BusinessException("邮箱已经存在");
            }
        }
        String value = String.valueOf(redisUtils.get(toEmail));
        if (value.equals("null")) {
            redisUtils.setex(toEmail, 1, 3600);
            sendMail(toEmail);
        } else {
            int val = Integer.valueOf(value);
            if (val < 3){
                redisUtils.incr(toEmail);
                sendMail(toEmail);
            } else {
                throw new BusinessException("获取验证码频繁");
            }
        }

    }

    @Override
    public void checkCode(String email, String code) {
        EmailCode emailCode = emailCodeMapper.selectByEmailAndCode(email, code);
        // 如果没查到数据
        if (emailCode == null) {
            throw new BusinessException("邮箱验证码不正确");
        }
        // 如果已经失效或超时
        if (emailCode.getStatus() == 1 || System.currentTimeMillis() - emailCode.getCreateTime()
                .getTime() > Constants.LENGTH_15 * 1000 * 60) {
            throw new BusinessException("邮箱验证已失效");
        }

        emailCodeMapper.disableEmailCode(email);
    }

    /**
     * 发送邮箱验证码
     * @param toEmail 需要发送的邮箱
     */
    public void sendMail(String toEmail){
        String code = StringTools.getRandomNumber(Constants.LENGTH_5);
        sendEmailCode(toEmail, code);

        // 数据库中的其他验证码置于不可用
        emailCodeMapper.disableEmailCode(toEmail);

        // 封装EmailCode对象
        EmailCode emailCode = new EmailCode();
        emailCode.setCode(code);
        emailCode.setEmail(toEmail);
        emailCode.setStatus(Constants.REGISTER_ZERO);
        emailCode.setCreateTime(new Date());

        // 插入数据库
        emailCodeMapper.insert(emailCode);
    }

}