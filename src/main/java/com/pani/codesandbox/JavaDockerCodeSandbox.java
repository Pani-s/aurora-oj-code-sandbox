package com.pani.codesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.pani.codesandbox.docker.DockerTest;
import com.pani.codesandbox.model.ExecuteCodeRequest;
import com.pani.codesandbox.model.ExecuteCodeResponse;
import com.pani.codesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Pani
 * @date Created in 2024/3/12 13:19
 * @description
 */
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox {

    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("main---" + executeCodeResponse);

    }
    //    /**
    //     * 存放用户临时代码的总目录的名字
    //     */
    //    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    //
    //    /**
    //     * 用户代码 统一为类名叫 Main的java文件
    //     */
    //    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //
    //    /**
    //     * 存放用户临时代码的总目录的path路径
    //     */
    //    private static final String GLOBAL_CODE_DIR_PATH;

    private static final String GLOBAL_CODE_DIR_PATH_LINUX = "/root/code/aurora-oj-sandbox" + "/" + GLOBAL_CODE_DIR_NAME;

    //    /**
    //     * 目前定死的执行最大时间 limit 不然超时
    //     */
    //    private static final long TIME_OUT = 5000L;

    /**
     * 用户构建 java8 的镜像：拉取java8的镜像，只用执行一次就够了，所以放到dockerTest那里了
     */
    private static final Boolean FIRST_INIT = true;

    /**
     * jdk8 镜像
     */
    private final String JAVA_IMAGE = "openjdk:8-alpine";

    //    static {
    //        /*
    //            存放执行这些代码的目录，检查有没有
    //         */
    //        //先得到用户的工作目录，tmpCode文件夹在根目录下
    //        String userDir = System.getProperty("user.dir");
    //        GLOBAL_CODE_DIR_PATH = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
    //        //不同系统文件分隔符 separator 不一样
    //        if (!FileUtil.exist(GLOBAL_CODE_DIR_PATH)) {
    //            FileUtil.mkdir(GLOBAL_CODE_DIR_PATH);
    //        }
    //    }



    @Override
    List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        // 3. 创建容器，把文件复制到容器内
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        // 获取Docker Client
        //        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        try (DockerClient dockerClient = DockerTest.createDockerClient()) {
            // 创建容器

            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(JAVA_IMAGE);
            //限制 该容器运行时占用的资源
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(100 * 1000 * 1000L);
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
            //hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));

            //路径映射
            //【3 WAYS: 第一种linux运行，第二种远程运行并且文件夹能同步(idea同步不了QAQ)，第三种远程运行文件夹固定】
            //            String userCodeDirPath = userCodeFile.getParentFile().getAbsolutePath();
            //            hostConfig.setBinds(new Bind(userCodeDirPath, new Volume("/app")));
//            String userCodeDirPathLinux = GLOBAL_CODE_DIR_PATH_LINUX + "/" + uuidDirName;
//            hostConfig.setBinds(new Bind(userCodeDirPathLinux, new Volume("/app")));
            hostConfig.setBinds(new Bind("/root/code/aurora-oj-sandbox/tmpCode/5fd31c4a-2dcc-4f21-8c22-2b72055c9af3", new Volume("/app")));

            CreateContainerResponse createContainerResponse = containerCmd
                    .withHostConfig(hostConfig)
                    //禁网
                    .withNetworkDisabled(true)
                    //不能向 root 根目录写文件
                    .withReadonlyRootfs(true)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(true)
                    .exec();
            String containerId = createContainerResponse.getId();

            // 创建容器
            dockerClient.startContainerCmd(containerId).exec();

            // 执行命令并获取结果
            for (String inputArgs : inputList) {
                StopWatch stopWatch = new StopWatch();
                //计时
                String[] inputArgsArray = inputArgs.split(" ");
                //不然可能会被当作字符串
                String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();
                //System.err.println("创建执行命令：" + execCreateCmdResponse);

                ExecuteMessage executeMessage = new ExecuteMessage();
                final String[] message = {null};
                final String[] errorMessage = {null};

                long time = 0L;
                // 判断是否超时
                final boolean[] timeout = {true};

                String execId = execCreateCmdResponse.getId();
                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    //完成的时候调用
                    @Override
                    public void onComplete() {
                        // 如果执行完成，则表示没超时
                        timeout[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onNext(Frame frame) {
                        System.err.println("执行 - onNext");
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                            System.err.println("输出错误结果：" + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            System.err.println("输出结果：" + message[0]);
                        }
                        super.onNext(frame);
                    }
                };
                final long[] maxMemory = {0L};

                // 获取占用的内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        //可能测不到几个、因为执行时间太快了
                        System.err.println("内存占用：" + statistics.getMemoryStats().getUsage());
                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                    }

                    @Override
                    public void close() {

                    }

                    @Override
                    public void onStart(Closeable closeable) {

                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
                //执行统计
                System.err.println("启动检测====启动容器");
                statsCmd.exec(statisticsResultCallback);
                try {
                    /*启动容器*/
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                    statsCmd.close();
                } catch (InterruptedException e) {
                    System.err.println("程序执行异常");
                    throw new RuntimeException(e);
                }
                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
            }
            System.err.println(executeMessageList);
        } catch (Exception e) {
            System.err.println("程序执行异常BIG");
            throw new RuntimeException("主程序执行错误", e);
        }
        return executeMessageList;
    }

//    @Override
//    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
//        //到生成编译文件，都和之前实现的java原生codesandbox 相同：
//        List<String> inputList = executeCodeRequest.getInputList();
//        //目前只实现了Java语言
//        //String language = executeCodeRequest.getLanguage();
//        String code = executeCodeRequest.getCode();
//        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
//
//        //把用户的代码，创建文件夹隔离存放，名字的话，uuid好了
//        //该代码的临时存放目录的路径
//        String userCodeDirPath = GLOBAL_CODE_DIR_PATH + File.separator + UUID.randomUUID();
//        String userCodeDirPathLinux = GLOBAL_CODE_DIR_PATH_LINUX + "/" + UUID.randomUUID();
//        //用户的类 必须 要叫Main
//        String userCodePath = userCodeDirPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
//        String userCodePathLinux = userCodeDirPathLinux + "/" + GLOBAL_JAVA_CLASS_NAME;
//        //写进来
//        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
//
//        //使用 Process 类在终端执行命令
//        String compileCmdStr = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
//        try {
//            Process compileProcess = Runtime.getRuntime().exec(compileCmdStr);
//            ExecuteMessage executeMessageCompile = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
//            int exitValue = executeMessageCompile.getExitValue();
//            String errorMessage = executeMessageCompile.getErrorMessage();
//            if (exitValue != 0) {
//                JudgeInfo judgeInfo = new JudgeInfo();
//                judgeInfo.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getValue());
//                executeCodeResponse.setJudgeInfo(judgeInfo);
//                executeCodeResponse.setMessage("代码编译失败！" + errorMessage);
//                System.err.println(executeCodeResponse);
//                return executeCodeResponse;
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//
//        //睡会儿
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//
//        // 3. 创建容器，把文件复制到容器内
//        List<ExecuteMessage> executeMessageList = new ArrayList<>();
//        // 获取Docker Client
//        //        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        try (DockerClient dockerClient = DockerTest.createDockerClient()) {
//            // 创建容器
//
//            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(JAVA_IMAGE);
//            //限制 该容器运行时占用的资源
//            HostConfig hostConfig = new HostConfig();
//            hostConfig.withMemory(100 * 1000 * 1000L);
//            hostConfig.withMemorySwap(0L);
//            hostConfig.withCpuCount(1L);
//            //            hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
//            //路径映射
//            hostConfig.setBinds(new Bind(userCodeDirPathLinux, new Volume("/app")));
//            //            hostConfig.setBinds(new Bind("/root/code/aurora-oj-sandbox/tmpCode/5fd31c4a-2dcc-4f21-8c22-2b72055c9af3", new Volume("/app")));
//            CreateContainerResponse createContainerResponse = containerCmd
//                    .withHostConfig(hostConfig)
//                    //禁网
//                    .withNetworkDisabled(true)
//                    //不能向 root 根目录写文件
//                    .withReadonlyRootfs(true)
//                    .withAttachStdin(true)
//                    .withAttachStderr(true)
//                    .withAttachStdout(true)
//                    .withTty(true)
//                    .exec();
//            //            System.err.println("createContainerResponse "+createContainerResponse);
//            String containerId = createContainerResponse.getId();
//
//            System.err.println("创建容器");
//            // 创建容器
//            dockerClient.startContainerCmd(containerId).exec();
//
//            // docker exec keen_blackwell java -cp /app Main 1 3
//            // 执行命令并获取结果
//
//            for (String inputArgs : inputList) {
//                StopWatch stopWatch = new StopWatch();
//                //计时
//                String[] inputArgsArray = inputArgs.split(" ");
//                //不然可能会被当作字符串
//                String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
//                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
//                        .withCmd(cmdArray)
//                        .withAttachStderr(true)
//                        .withAttachStdin(true)
//                        .withAttachStdout(true)
//                        .exec();
//                System.err.println("创建执行命令：" + execCreateCmdResponse);
//
//                ExecuteMessage executeMessage = new ExecuteMessage();
//                final String[] message = {null};
//                final String[] errorMessage = {null};
//
//                long time = 0L;
//                // 判断是否超时
//                final boolean[] timeout = {true};
//                String execId = execCreateCmdResponse.getId();
//                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
//                    //完成的时候调用
//                    @Override
//                    public void onComplete() {
//                        // 如果执行完成，则表示没超时
//                        timeout[0] = false;
//                        super.onComplete();
//                    }
//
//                    @Override
//                    public void onNext(Frame frame) {
//                        System.err.println("执行 - onNext");
//                        StreamType streamType = frame.getStreamType();
//                        if (StreamType.STDERR.equals(streamType)) {
//                            errorMessage[0] = new String(frame.getPayload());
//                            System.err.println("输出错误结果：" + errorMessage[0]);
//                        } else {
//                            message[0] = new String(frame.getPayload());
//                            System.err.println("输出结果：" + message[0]);
//                        }
//                        super.onNext(frame);
//                    }
//                };
//                final long[] maxMemory = {0L};
//
//                // 获取占用的内存
//                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
//                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
//
//                    @Override
//                    public void onNext(Statistics statistics) {
//                        //可能测不到几个、因为执行时间太快了
//                        System.err.println("内存占用：" + statistics.getMemoryStats().getUsage());
//                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
//                    }
//
//                    @Override
//                    public void close() throws IOException {
//
//                    }
//
//                    @Override
//                    public void onStart(Closeable closeable) {
//
//                    }
//
//                    @Override
//                    public void onError(Throwable throwable) {
//
//                    }
//
//                    @Override
//                    public void onComplete() {
//
//                    }
//                });
//                //执行统计
//                System.err.println("启动检测=====启动容器");
//                statsCmd.exec(statisticsResultCallback);
//                try {
//                    /*
//                    启动容器 原神启动！
//                     */
//                    stopWatch.start();
//                    dockerClient.execStartCmd(execId)
//                            .exec(execStartResultCallback)
//                            //                            .awaitCompletion();
//                            .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
//                    stopWatch.stop();
//                    time = stopWatch.getLastTaskTimeMillis();
//                    statsCmd.close();
//                } catch (InterruptedException e) {
//                    System.err.println("程序执行异常");
//                    throw new RuntimeException(e);
//                }
//                executeMessage.setMessage(message[0]);
//                executeMessage.setErrorMessage(errorMessage[0]);
//                executeMessage.setTime(time);
//                executeMessage.setMemory(maxMemory[0]);
//                executeMessageList.add(executeMessage);
//            }
//            System.err.println(executeMessageList);
//        } catch (Exception e) {
//            System.err.println("程序执行异常BIG");
//            throw new RuntimeException(e);
//        }
//        // 4、封装结果，跟原生实现方式完全一致
//        List<String> outputList = new ArrayList<>();
//        // 取用时最大值，便于判断是否超时
//        long maxTime = 0;
//        for (ExecuteMessage executeMessage : executeMessageList) {
//            String errorMessage = executeMessage.getErrorMessage();
//            if (StrUtil.isNotBlank(errorMessage)) {
//                executeCodeResponse.setMessage(errorMessage);
//                // 用户提交的代码执行中存在错误
//                executeCodeResponse.setStatus(3);
//                break;
//            }
//            outputList.add(executeMessage.getMessage());
//            Long time = executeMessage.getTime();
//            if (time != null) {
//                maxTime = Math.max(maxTime, time);
//            }
//        }
//        // 正常运行完成
//        if (outputList.size() == executeMessageList.size()) {
//            executeCodeResponse.setStatus(1);
//        }
//        executeCodeResponse.setOutputList(outputList);
//        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setTime(maxTime);
//        // 要借助第三方库来获取内存占用，非常麻烦，此处不做实现
//        // judgeInfo.setMemory();
//
//        executeCodeResponse.setJudgeInfo(judgeInfo);
//
//        //最后：文件清理
//        if (userCodeFile.getParentFile() != null) {
//            boolean del = FileUtil.del(userCodeDirPath);
//            System.out.println("删除" + (del ? "成功" : "失败"));
//        }
//        return executeCodeResponse;
//    }


}
