package com.wjp.wcloudatlasbackend.api.imagesearch;

import com.wjp.wcloudatlasbackend.api.imagesearch.model.ImageSearchResult;
import com.wjp.wcloudatlasbackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.wjp.wcloudatlasbackend.api.imagesearch.sub.GetImageListApi;
import com.wjp.wcloudatlasbackend.api.imagesearch.sub.GetImagePageUrlApi;

import java.util.List;

/**
 * 门面模式 将流程组成在一起，供客户端调用
 */
public class ImageSearchApiFacade {

    /**
     * 搜索图片 【门面模式】
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // 1. 获取以图搜图页面地址
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);

        // 2. 获取图片列表页面地址
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);

        // 3. 获取图片列表
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);

        return imageList;
    }


    public static void main(String[] args) {
        List<ImageSearchResult> imageSearchResults = searchImage("https://w-cloud-atlas-1308962059.cos.ap-shanghai.myqcloud.com/space/1883151781463601153/2025-01-27_mhH8HyHlTkK8ncZ3_thumbnail.jpg");
        System.out.println("搜索成功: " + imageSearchResults);
    }

}
