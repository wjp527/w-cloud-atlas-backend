package com.wjp.wcloudatlasbackend;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BatchPicture {

    /**
     * 批量导入百度的图片
     * @param args
     */
    @Test
    public static void main(String[] args) {
        /**
         * tn=resultjson_com：必带的参数
         * word：搜索关键词
         * pn：分页数，传入30的倍数，第一次为30，第二次为60，以此类推
         */
        String urlTemplate = "https://images.baidu.com/search/acjson?tn=resultjson_com&word=%s&pn=%s";
        String queryTxt = "美女,校园";
        int pageSize = 30;  // 每页数据数量
        int page = 1;       // 起始页，从第几页开始
        int count = 2;     // 总共查询的数据条数
        int totalCount = 0; // 已查询的总条数

        // 从指定的起始页开始循环
        while (totalCount < count) {
            int pn = (page - 1) * pageSize; // 计算分页参数
            String url = String.format(urlTemplate, queryTxt, pn);
            String s = HttpUtil.get(url);
            JSONObject jsonObject = JSONUtil.parseObj(s);
            JSONArray list = jsonObject.getJSONArray("data");

            if (list == null || list.isEmpty()) {
                System.out.println("第" + page + "页无数据，结束查询。");
                break;
            }

            System.out.println("第" + page + "页查询结果 ====");
            for (int j = 0; j < list.size(); j++) {
                if (totalCount >= count) {
                    break; // 达到总查询条数限制
                }

                JSONObject res = list.get(j, JSONObject.class);
                String thumbURL = res.getStr("thumbURL");
                if (thumbURL != null) { // 避免空值
                    totalCount++;
                    System.out.println(totalCount + "=======" + thumbURL);
                }
            }

            page++; // 查询下一页
        }

        System.out.println("查询结束，共输出 " + totalCount + " 条数据。");
    }


}
