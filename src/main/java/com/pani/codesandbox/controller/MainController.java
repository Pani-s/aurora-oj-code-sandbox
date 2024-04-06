package com.pani.codesandbox.controller;

import cn.hutool.crypto.SecureUtil;
import com.pani.codesandbox.JavaDockerCodeSandbox;
import com.pani.codesandbox.JavaDockerOldCodeSandbox;
import com.pani.codesandbox.JavaNativeCodeSandbox;
import com.pani.codesandbox.model.ExecuteCodeRequest;
import com.pani.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Pani
 * @date Created in 2024/3/13 9:18
 * @description
 */
@Slf4j
@RestController
@RequestMapping("/")
public class MainController {
    /**
     * 定义鉴权请求头和密钥
     */
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = SecureUtil.md5("kookv");

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @Resource
    private JavaDockerOldCodeSandbox javaDockerOldCodeSandbox;

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;


    /**
     * 执行代码--docker old
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                    HttpServletResponse response) {
        log.info("executeCode请求 coming---docker old");
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            //403 forbidden  401 Unauthorized
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        //        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
        return javaDockerOldCodeSandbox.executeCode(executeCodeRequest);
    }

    /**
     * 执行代码--java native
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode/java")
    ExecuteCodeResponse executeCodeJava(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                        HttpServletResponse response) {
        log.info("executeCode请求 coming java native");
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            //403 forbidden  401 Unauthorized
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }

    /**
     * 执行代码 -- docker new
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode/ACM")
    ExecuteCodeResponse executeCodeACM(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                       HttpServletResponse response) {
        log.info("executeCode请求 coming--docker new");
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            //403 forbidden  401 Unauthorized
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }

}
