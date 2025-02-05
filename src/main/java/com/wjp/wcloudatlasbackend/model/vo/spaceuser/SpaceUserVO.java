package com.wjp.wcloudatlasbackend.model.vo.spaceuser;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间用户信息 VO
 * @author wjp
 */
@Data
public class SpaceUserVO implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 用户信息
     */
    private UserVO user;

    /**
     * 空间信息
     */
    private SpaceVO space;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


    /**
     * 封装类vo 转 对象
     * @param spaceUserVO 封装类vo
     * @return 对象
     */
    public static SpaceUser voToObj(SpaceUserVO spaceUserVO) {
        if(spaceUserVO == null) {
            return null;
        }

        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserVO, spaceUser);

        return spaceUser;
    }

    /**
     * 对象 转 封装类vo
     * @param spaceUser 对象
     * @return 封装类vo
     */

    public static SpaceUserVO objToVo(SpaceUser spaceUser) {
        if(spaceUser == null) {
            return null;
        }
        SpaceUserVO spaceUserVO = new SpaceUserVO();
        BeanUtils.copyProperties(spaceUser, spaceUserVO);
        return spaceUserVO;
    }



}
