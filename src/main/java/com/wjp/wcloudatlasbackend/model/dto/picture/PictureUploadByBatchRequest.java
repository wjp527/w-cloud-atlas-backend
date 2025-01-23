package com.wjp.wcloudatlasbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量导入图片对象
 * @author wjp
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {
    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 一次性上传数量(默认10条，不建议超过30条)
     */
    private Integer count = 10;

    /**
     * 图片名称前缀
     */
    private String namePrefix;

    /**
     * 图片来源
     */
    private String source;

    /**
     * 爬取几页
     */
    private Integer page;


    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    private static final long serialVersionUID = -4289982058773032480L;
}
