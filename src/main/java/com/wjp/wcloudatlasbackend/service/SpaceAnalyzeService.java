package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.analyze.*;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.model.vo.space.analyze.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author wjp
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-01-25 16:35:44
*/
public interface SpaceAnalyzeService extends IService<Space> {

    /**
     * 获取空间使用情况分析
     * @param spaceUsageAnalyzeRequest 空间资源使用分析
     * @param loginUser 登录用户
     * @return 空间资源使用分析结果
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 获取空间分析
     * @param spaceCategoryAnalyzeRequest 空间分析请求
     * @param loginUser 登录用户
     * @return 空间分析结果
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间标签分析
     * @param spaceTagAnalyzeRequest 空间分析请求
     * @param loginUser      登录用户
     * @return 空间标签分析结果
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片大小分析
     * @param spaceSizeAnalyzeRequest 空间查询请求
     * @param loginUser 登录用户
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 获取用户上传行为分析
     * @param spaceUserAnalyzeRequest 用户上传行为分析请求
     * @param loginUser 登录用户
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间使用排行分析
     * @param spaceRankAnalyzeRequest 空间使用排行分析请求
     * @param loginUser 登录用户
     * @return 空间使用排行分析结果
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
