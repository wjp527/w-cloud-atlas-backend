package com.wjp.wcloudatlasbackend.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 批量导入图片的枚举
 * @author wjp
 */
@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100, 100L * 1024 *1024),
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 *1024),
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 *1024);

    private final String text;
    private final int value;
    private final long maxCount;
    private final long maxSize;

    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    /**
     * 根据 value 获取枚举
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if(ObjectUtil.isEmpty(value)) {
            return null;
        }

        for (SpaceLevelEnum pictureReviewStatusEnum : SpaceLevelEnum.values()) {
            if(pictureReviewStatusEnum.value == value) {
                return pictureReviewStatusEnum;
            }
        }

        return null;
    }
}
