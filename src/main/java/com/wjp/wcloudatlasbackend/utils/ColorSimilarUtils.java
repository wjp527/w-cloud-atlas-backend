package com.wjp.wcloudatlasbackend.utils;

import java.awt.*;

/**
 * 工具类: 计算颜色相似度
 * @author wjp
 */
public class ColorSimilarUtils {

    // 工具类不需要实例化
    public ColorSimilarUtils() {
    }

    /**
     * 计算两个颜色相似度
     * @param color1 第一个颜色
     * @param color2 第二个颜色
     * @return 相似度
     */
    public static double calculateSimilarity(Color color1, Color color2) {
        int red1 = color1.getRed();
        int green1 = color1.getGreen();
        int blue1 = color1.getBlue();

        int red2 = color2.getRed();
        int green2 = color2.getGreen();
        int blue2 = color2.getBlue();

        // 采用欧几里得，计算欧式距离
        double distance = Math.sqrt(Math.pow(red1 - red2,2) + Math.pow(green1 - green2,2) + Math.pow(blue1 - blue2,2));

        // 计算相似度
        return 1 - distance / Math.sqrt(3 * Math.pow(255,2));
    }

    /**
     * 根据十六进制颜色值 计算两个颜色的相似度
     * @param hexColor1 第一个颜色的十六进制颜色值
     * @param hexColor2 第二个颜色的十六进制颜色值
     * @return 相似度（0到1之间，1为完全相同）
     */
    public static double calculateSimilarity(String hexColor1, String hexColor2) {
        // 转换为Color对象
        Color color1 = Color.decode(hexColor1);
        // 转换为Color对象
        Color color2 = Color.decode(hexColor2);
        return calculateSimilarity(color1, color2);
    }


    /**
     * 测试方法
     * @param args
     */
    public static void main(String[] args) {
        // 测试颜色
        Color color1 = Color.decode("0xFF0000");
        Color color2 = Color.decode("0xFe0101");

        double similarity = calculateSimilarity(color1, color2);

        System.out.println("颜色相似度: " + similarity);

        // 测试十六进制方法
        double hexSimilarity = calculateSimilarity("0xFF0000", "0xFe0101");
        System.out.println("十六进制颜色相似度为: " + hexSimilarity);


    }


}
