package com.wjp.wcloudatlasbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.auth.StpKit;
import com.wjp.wcloudatlasbackend.model.dto.user.UserQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.user.UserRegisterRequest;
import com.wjp.wcloudatlasbackend.model.dto.user.VipCode;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.UserRoleEnum;
import com.wjp.wcloudatlasbackend.model.vo.user.LoginUserVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.UserService;
import com.wjp.wcloudatlasbackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author wjp
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-01-17 19:26:44
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    // 用于加密数据
    private final String SLAT = "wjp";

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求参数
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // 1. 获取用户注册参数
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        // 2. 检查用户账号是否和数据库中已有的数据冲突
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length()<3,ErrorCode.PARAMS_ERROR, "账号长度不能小于3位");
        ThrowUtils.throwIf(userPassword.length()<8||checkPassword.length()<8,ErrorCode.PARAMS_ERROR, "密码长度不能小于8位");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次密码不一致");

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        // 判断该账号是否重复
        long count = this.baseMapper.selectCount(queryWrapper);
        if(count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已存在");
        }
        // 3. 对密码及逆行加密
        String encryptPassword = getEncryptPassword(userPassword);

        // 4. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        // 普通用户权限
        user.setUserRole(UserRoleEnum.USER.getValue());

        boolean saveResult = this.save(user);
        if(!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户注册失败");
        }

        return user.getId();

    }


    /**
     * 用户登录
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param request 请求对象 - 用于种session信息
     * @return
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if(StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        ThrowUtils.throwIf(userAccount.length()<3,ErrorCode.PARAMS_ERROR, "账号长度错误");
        ThrowUtils.throwIf(userPassword.length()<8,ErrorCode.PARAMS_ERROR, "密码长度错误");

        // 2. 对用户传递的密码及逆行加密
        String encryptPassword = getEncryptPassword(userPassword);

        // 3. 查询数据库种的用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 不存在，抛异常
        if(user == null) {
            log.info("user login fail, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 保存用户的登录状态
        // request.getSession(): 获取当前请求的session对象
        // 将用户信息保存到session中
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);

        // 记录用户登录态到 Sa-token，使于空间鉴权时使用，注意保证该用户信息 与 SpringSession 中的信息过期时间一致
        // 在当前会话进行 Space 账号登录
        StpKit.SPACE.login(user.getId());
        // 获取当前 Space 会话的 Session 对象，并进行写值操作
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);

        LoginUserVO loginUserVO = getLoginUserVO(user);
        return loginUserVO;
    }

    /**
     * 获取当前登录用户信息
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if(currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }

        // 从数据库种查询(追求性能的化可以注释，直接返回上述结果，不过这里为了缓存，还是查询一下数据库)
        Long userId = currentUser.getId();
        currentUser = this.baseMapper.selectById(userId);
        if(currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return currentUser;
    }

    /**
     * 用户注销
     * @param request 请求对象
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 判断是否已经登录
        if(userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }


    /**
     * 获取加密后的密钥
     * @param userPassword
     * @return
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 对密码及逆行加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SLAT + userPassword).getBytes());
        return encryptPassword;

    }

    /**
     * 获取脱敏类的用户登录信息
     * @param user
     * @return
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if(user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获取脱敏类的用户信息
     * @param user 用户
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if(user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏类的用户信息列表
     * @param userList 用户列表
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if(CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }

        // 老写法
        //         List<UserVO> userVOList = userList.stream()
        //                .map(user -> getUserVO(user))
        //                .collect(Collectors.toList());

        // 新写法
        List<UserVO> userVOList = userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());

        return userVOList;
    }

    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param userQueryRequest 查询条件
     * @return
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if(userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询条件为空");
        }

        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        // 构建查询条件
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotNull(id),"id",id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }

    /**
     * 判断用户是否是管理员
     * @param user
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());

    }


    // region  会员相关业务实现
    private static final String VIP_CODE_FILE_PATH = "src/main/resources/biz/vipCode.json";

    /**
     * 用户兑换会员 (会员码兑换)
     * @param user 用户
     * @param vipCode 会员码
     * @return 是否兑换成功
     */
    @Override
    public boolean exchangeVip(User user, String vipCode) {

        // 1. 参数校验
        if(user == null || vipCode == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }

        User loginUser = this.getById(user.getId());
        if(loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }

        // 如果已经是会员就无需兑换
        if(loginUser.getUserRole().equals(UserConstant.VIP_ROLE)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已经是会员");
        }

        // 2. 读取 VIP 会员码文件并进行校验
        List<VipCode> vipCodes = loadVipCodes();

        Optional<VipCode> vipCodeOpt = vipCodes.stream()
                .filter(code -> code.getCode().equals(vipCode) && !code.isHasUsed())
                .findFirst();

        // 校验会员码是否有效
        if (!vipCodeOpt.isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会员码无效或已被使用");
        }

        // 获取到未使用的会员码
        VipCode validVipCode = vipCodeOpt.get();

        // 3. 兑换会员：更新用户信息
        loginUser.setUserRole(UserConstant.VIP_ROLE);
        loginUser.setVipCode(vipCode);
        loginUser.setVipExpireTime(calculateVipExpireTime());


        loginUser.setVipNumber(generateVipNumber());

        // 更新数据库
        boolean result = this.updateById(loginUser);

        if(!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "兑换会员失败");
        }

        // 4. 标记会员码为已使用
        validVipCode.setHasUsed(true);

        // 将更新后的 vipCode 数据写回文件
        saveVipCodes(vipCodes);

        return true;
    }

    // 读取会员码 JSON 文件
    private List<VipCode> loadVipCodes() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // 读取 JSON 文件并转为对象
            return objectMapper.readValue(new File(VIP_CODE_FILE_PATH),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, VipCode.class));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加载会员码失败");
        }
    }

    // 保存更新后的会员码信息到文件
    private void saveVipCodes(List<VipCode> vipCodes) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // 将会员码列表写回 JSON 文件
            objectMapper.writeValue(new File(VIP_CODE_FILE_PATH), vipCodes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存会员码失败");
        }
    }

    // 计算会员过期时间（假设会员有效期为 1 年）
    private Date calculateVipExpireTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1); // 会员有效期为1年
        return calendar.getTime();
    }

    // 随机生成会员编号
    private Integer generateVipNumber() {
        Random random = new Random();
        return random.nextInt(1000000);
    }

    // endregion
}




