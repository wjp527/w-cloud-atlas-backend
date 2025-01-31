package com.wjp.wcloudatlasbackend.api.imagesearch.sub;

import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 获取图片列表页面地址
 * @author wjp
 */
@Slf4j
public class GetImageFirstUrlApi {

    /**
     * 获取图片列表页面地址
     * @param url
     * @return
     */
    public static String getImageFirstUrl(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(5000)
                    .get();

            // 获取所有 script 标签
            Elements scriptElements = document.getElementsByTag("script");

            // 遍历找到包含 firstUrl 的脚本内容
            for (Element script : scriptElements) {
                String scriptContent = script.html();
                if(scriptContent.contains("\"firstUrl\"")) {
                    // 正则表达式获取 firstUrl 的值
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if(matcher.find()) {
                        String firstUrl = matcher.group(1);
                        // 处理 转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }

                }
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到URL");
        } catch(Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 请求目标URL
        String url = "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData[isLogoShow]=1&f=all&isLogoShow=1&session_id=8328517542964920283&sign=126d0e97cd54acd88139901738227358&tpl_from=pc";
        String imageFirstUrl = getImageFirstUrl(url);
        System.out.println("搜索成功: " + imageFirstUrl);
    }

}
