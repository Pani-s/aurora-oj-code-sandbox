package com.pani.auroracodesandbox.security;

import cn.hutool.core.io.FileUtil;

import java.nio.charset.Charset;

/**
 * @author Pani
 * @date Created in 2024/3/9 16:00
 * @description
 */
public class TestSecurityManager {
    public static void main(String[] args) {
        System.setSecurityManager(new MySecurityManager());
        FileUtil.writeString("aa", "aaa", Charset.defaultCharset());
    }
}
