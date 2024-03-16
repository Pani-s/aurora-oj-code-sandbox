package com.pani.codesandbox.security;

import java.security.Permission;

/**
 * @author Pani
 * @date Created in 2024/3/9 15:59
 * @description
 * 1. 根据需要开发自定义的安全管理器（比如 MySecurityManager）
 * 2.复制 MySecurityManager 类到 resources/security 目录下，移除类的包名
 * 3. 手动输入命令编译 MySecurityManager 类，得到 class 文件
 * 4. 在运行 java 程序时，指定安全管理器 class 文件的路径、安全管理器的名称。
 */
public class MySecurityManager extends SecurityManager {

    /**
     * 检查所有的权限
     */
    @Override
    public void checkPermission(Permission perm) {
        //默认 all禁
        //super.checkPermission(perm);
    }

    /**
     * 检测程序是否可执行文件
     */
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }

    /**
     * 检测程序是否允许读文件
     *
     * @param file the system-dependent file name.
     */
    @Override
    public void checkRead(String file) {
//        System.out.println(file);
//        if (file.contains("C:\\code\\yuoj-code-sandbox")) {
//            return;
//        }
        //        throw new SecurityException("checkRead 权限异常：" + file);
    }

    /**
     * 检测程序是否允许写文件
     *
     * @param file the system-dependent filename.
     */
    @Override
    public void checkWrite(String file) {
        // throw new SecurityException("checkWrite 权限异常：" + file);
    }

    /**
     * 检测程序是否允许删除文件
     *
     * @param file the system-dependent filename.
     */
    @Override
    public void checkDelete(String file) {
        //        throw new SecurityException("checkDelete 权限异常：" + file);
    }

    /**
     * 检测程序是否允许连接网络
     *
     * @param host the host name port to connect to.
     * @param port the protocol port to connect to.
     */
    @Override
    public void checkConnect(String host, int port) {
        //        throw new SecurityException("checkConnect 权限异常：" + host + ":" + port);
    }

}
