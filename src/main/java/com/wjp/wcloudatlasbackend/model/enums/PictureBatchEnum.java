package com.wjp.wcloudatlasbackend.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 批量导入图片的枚举
 * @author wjp
 */
@Getter
public enum PictureBatchEnum {

    BAIDU("百度","baidu"),
    BING("必应", "bing");

    private final String text;
    private final String value;

    PictureBatchEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static PictureBatchEnum getEnumByValue(String value) {
        if(ObjectUtil.isEmpty(value)) {
            return null;
        }

        for (PictureBatchEnum pictureReviewStatusEnum : PictureBatchEnum.values()) {
            if(pictureReviewStatusEnum.value == value) {
                return pictureReviewStatusEnum;
            }
        }

        return null;
    }
}
