package com.wjp.wcloudatlasbackend.api.imagesearch;

import com.wjp.wcloudatlasbackend.api.imagesearch.model.ImageSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片搜索API
 * @author wjp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSearchApiFacade {


    private final PexelsImageSearch pexelsImageSearch;


    /**
     * 搜索图片
     *
     * @param query 图片关键字（中文）
     * @return 图片搜索结果列表
     */
    public List<ImageSearchResult> searchImage(String query) {

        // 先进行中文转英文
        // 这样创建对象不会通过 Spring 容器进行管理
        // PexelsImageSearch imageSearch = new PexelsImageSearch();
        // 通过 Spring 容器来管理对象
        // 先进行中文转英文
        List<String> imageUrls = pexelsImageSearch.searchPicturesForChinese(query, 18);

        // 将图片的URL转为ImageSearchResult对象
        return convertToImageSearchResults(imageUrls);
    }


    /**
     * 将图片URL列表转换为ImageSearchResult列表
     *
     * @param imageUrls 图片URL列表
     * @return ImageSearchResult列表
     */
    private static List<ImageSearchResult> convertToImageSearchResults(List<String> imageUrls) {
        List<ImageSearchResult> imageSearchResults = new ArrayList<>();
        for (String url : imageUrls) {
            ImageSearchResult result = new ImageSearchResult();
            // 使用Pexels提供的原图URL作为缩略图
            result.setThumbUrl(url);
            // 这里假设来源地址与缩略图相同
            result.setFromUrl(url);
            imageSearchResults.add(result);
        }
        return imageSearchResults;
    }

    public static void main(String[] args) {
//        // 测试通过中文关键词进行图片搜索
//        String query = "斗破苍穹";
//        List<ImageSearchResult> imageList = ImageSearchApiFacade.searchImage(query);
//        System.out.println("搜索成功，获取到图片列表：" + imageList);
    }
}