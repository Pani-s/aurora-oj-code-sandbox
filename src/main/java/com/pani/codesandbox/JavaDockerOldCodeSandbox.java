package com.pani.codesandbox;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.pani.codesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Pani
 * @date Created in 2024/3/12 13:19
 * @description
 */
@Slf4j
@Component
public class JavaDockerOldCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox {
/*    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("main---" + executeCodeResponse);

    }*/

    /**
     * // 设置最大容器数 , 因为还有本来的容器
     */
    private static final int MAX_CONTAINERS = 10;

    private static final String GLOBAL_CODE_DIR_PATH_LINUX = "/root/code/aurora-oj-sandbox" + "/" + GLOBAL_CODE_DIR_NAME;

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
        /*
        DockerClient dockerClient = DockerTest.createDockerClient()
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
         */
        try (DockerClient dockerClient = DockerClientBuilder.getInstance().build()) {
            //创建容器前先限制代码沙箱中最多允许同时启动的 Docker 容器数

            //列出位于Docker主机上的所有正在运行的容器。
            List<Container> containers = dockerClient.listContainersCmd().
                    withShowAll(false).exec();
            if (containers.size() > MAX_CONTAINERS) {
                log.error("容器数量达到限制~");
                throw new RuntimeException("容器数量达到限制~");
            }

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
            String userCodeDirPath = userCodeFile.getParentFile().getAbsolutePath();
            hostConfig.setBinds(new Bind(userCodeDirPath, new Volume("/app")));
            /*
            ---------------
             */
            //            String userCodeDirPathLinux = GLOBAL_CODE_DIR_PATH_LINUX + "/" + uuidDirName;
            //            hostConfig.setBinds(new Bind(userCodeDirPathLinux, new Volume("/app")));
            /*
            ------------------------------
             */
            //            hostConfig.setBinds(new Bind("/root/code/aurora-oj-sandbox/tmpCode/5fd31c4a-2dcc-4f21-8c22-2b72055c9af3", new Volume("/app")));

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

                /*
                'com.github.dockerjava.core.command.ExecStartResultCallback' 已经过时了
                 */
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
                //
                //                };
                ResultCallback.Adapter<Frame> adapter = new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        log.error("执行 - onNext");
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                            log.error("输出错误结果：" + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            log.info("输出结果：" + message[0]);
                        }
                        super.onNext(frame);
                    }

                    @Override
                    public void onComplete() {
                        // 如果执行完成，则表示没超时
                        timeout[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // 执行出错时调用
                        errorMessage[0] += "error:" + throwable.getMessage();
                        log.error("输出错误结果：" + errorMessage[0]);
                    }
                };
                final long[] maxMemory = {0L};
                // 获取占用的内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        //可能测不到几个、因为执行时间太快了
                        log.info("内存占用：" + statistics.getMemoryStats().getUsage());
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
                log.error("启动检测====启动容器");
                statsCmd.exec(statisticsResultCallback);
                try {
                    /*启动容器*/
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .exec(adapter)
                            //限制最大执行时间 microseconds微秒 milliseconds毫秒
                            //                            .awaitCompletion();
                            .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                    statsCmd.close();
                    adapter.close();
                } catch (InterruptedException e) {
                    log.error("程序执行异常");
                    throw new RuntimeException(e);
                }
                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
            }
            log.info("executeMessageList = {}", executeMessageList);
            //删除容器
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.error("程序执行异常BIG");
            throw new RuntimeException("主程序执行错误", e);
        }
        return executeMessageList;
    }


}
