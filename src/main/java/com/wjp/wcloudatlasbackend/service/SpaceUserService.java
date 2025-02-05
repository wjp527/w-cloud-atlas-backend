package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import com.wjp.wcloudatlasbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.spaceuser.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author wjp
* @description 针对表【spaceUser_user(空间成员用户关联)】的数据库操作Service
* @createDate 2025-02-04 15:19:53
*/
public interface SpaceUserService extends IService<SpaceUser> {
    // ---------------------------- start 通用代码---------------------------------

    /**
     * 校验空间成员是否合法 【add = true 新增，false 修改】
     * @param spaceUser
     * @param add
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员包装类(单条)
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);


    /**
     * 获取空间成员包装类(列表)
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);


    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param spaceUserQueryRequest 查询条件
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);


    // ---------------------------- end 通用代码---------------------------------


    /**
     * 创建空间成员
     * @param spaceUserAddRequest 请求体
     * @return 创建的空间成员id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);


}
