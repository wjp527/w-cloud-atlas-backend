package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceQueryRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author wjp
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-01-25 16:35:44
*/
public interface SpaceService extends IService<Space> {
    // ---------------------------- start 通用代码---------------------------------

    /**
     * 校验空间是否合法 【add = true 新增，false 修改】
     * @param space
     * @param add
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类(单条)
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);


    /**
     * 获取空间包装类(分页)
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);


    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param spaceQueryRequest 查询条件
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);


    // ---------------------------- end 通用代码---------------------------------


    /**
     * 创建空间
     * @param spaceAddRequest 请求体
     * @param loginUser 登录用户
     * @return 创建的空间id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 根据空间级别填充空间对象
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);



}
