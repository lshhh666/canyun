package com.sky.service;

import com.alibaba.fastjson.JSONObject;
import com.sky.properties.BaiduMapProperties;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.security.MessageDigest;

@Service
@Slf4j
public class BaiduMapService {

    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3/";
    private static final String DIRECTION_URL = "https://api.map.baidu.com/directionlite/v1/driving";

    @Autowired
    private BaiduMapProperties baiduMapProperties;

    /**
     * 地理编码：地址转经纬度
     */
    public JSONObject geocoder(String address) {
        try {
            String params = "address=" + URLEncoder.encode(address, "UTF-8")
                    + "&output=json"
                    + "&ak=" + baiduMapProperties.getAk();
            String sn = generateSn("/geocoding/v3/", params);
            String url = GEOCODING_URL + "?" + params + "&sn=" + sn;
            log.info("地理编码请求URL: {}", url);
            String result = HttpClientUtil.doGet(url, null);
            log.info("地理编码响应: {}", result);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            log.error("地理编码请求失败", e);
            throw new RuntimeException("地理编码请求失败", e);
        }
    }

    /**
     * 路线规划：获取两点间驾车距离（米）
     */
    public JSONObject direction(String origin, String destination) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String params = "origin=" + origin
                    + "&destination=" + destination
                    + "&ak=" + baiduMapProperties.getAk()
                    + "&timestamp=" + timestamp;
            String sn = generateSn("/directionlite/v1/driving", params);
            String url = DIRECTION_URL + "?" + params + "&sn=" + sn;
            log.info("路线规划请求URL: {}", url);
            String result = HttpClientUtil.doGet(url, null);
            log.info("路线规划响应: {}", result);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            log.error("路线规划请求失败", e);
            throw new RuntimeException("路线规划请求失败", e);
        }
    }

    /**
     * 生成SN签名
     */
    private String generateSn(String path, String params) throws Exception {
        String sk = baiduMapProperties.getSk();
        String encodeStr = URLEncoder.encode(path + "?" + params + sk, "UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(encodeStr.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}