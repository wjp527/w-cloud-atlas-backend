package com.wjp.wcloudatlasbackend.utils;

/**
 * 颜色转换器工具类
 * @author wjp
 */
public class ColorTransformUtils {
    public ColorTransformUtils() {
    }

    /**
     * 获取标准颜色（将数据万象的 5位 色值 转为 6位）
     * @param color 主色调
     * @return 标准颜色
     */
    public static String getStanderColor(String color) {
        // 示例: 0x080e0 => 0x0800e
        if(color.length() == 7) {
            color = color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }

}
