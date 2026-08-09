package com.sky.text;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;


@SpringBootTest
public class HttpClientText {
    @Test
    public void textGET() throws IOException {
        //创建httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //创建请求对象
        HttpGet httpGet=new HttpGet("http://localhost:8080/user/shop/status");
        //发送请求,接收响应结果
        CloseableHttpResponse response = httpClient.execute(httpGet);
        //获取服务器返回的状态码
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("状态码"+statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("返回的数据"+body);
        //关闭资源
        httpClient.close();
        response.close();
    }
    @Test
    public void textPost() throws IOException {
        //创建httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //创建请求对象
        HttpPost httpPost=new HttpPost("http://localhost:8080/admin/employee/login");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username","admin");
        jsonObject.put("password","123456");

        StringEntity entity=new StringEntity(jsonObject.toString());
        //指定编码方式
        entity.setContentEncoding("UTF-8");
        //数据格式
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        //发送请求
        CloseableHttpResponse response = httpClient.execute(httpPost);
        //获取状态码
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("状态码"+statusCode);

        HttpEntity entity1 = response.getEntity();
        String body = EntityUtils.toString(entity1);
        System.out.println("返回的数据"+body);
        //关闭资源
        httpClient.close();
        response.close();
    }
}
