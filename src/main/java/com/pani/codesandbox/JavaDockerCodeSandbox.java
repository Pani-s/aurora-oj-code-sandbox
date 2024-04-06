package com.pani.codesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.common.collect.ImmutableMap;
import com.pani.codesandbox.exception.TLEException;
import com.pani.codesandbox.model.ExecuteCodeRequest;
import com.pani.codesandbox.model.ExecuteCodeResponse;
import com.pani.codesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
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
 * @description 新版支持ACM模式
 */
@Slf4j
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox {
    public static void main(String[] args) {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleAddACM/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("main---" + executeCodeResponse);

    }

    /**
     * // 设置最大容器数 , 因为还有本来的容器
     */
    private static final int MAX_CONTAINERS = 10;

    private static final String GLOBAL_CODE_DIR_PATH_LINUX = "/root/code/aurora-oj-sandbox" + "/" + GLOBAL_CODE_DIR_NAME;

    //    private static final String INPUT_FILE_NAME = "/app/input.txt";
    private static final String INPUT_FILE_NAME = "/run/input.txt";


    /**
     * jdk8 镜像
     */
    private final String JAVA_IMAGE = "openjdk:8-alpine";


    @Override
    List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        // 获取Docker Client
        /*
        DockerClient dockerClient = DockerTest.createDockerClient()
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
         */
        try (DockerClient dockerClient = DockerClientBuilder.getInstance().build()) {
            //创建容器前先限制代码沙箱中最多允许同时启动的 Docker 容器数
            //列出位于Docker主机上的所有正在运行的容器。
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(false).exec();
            if (containers.size() > MAX_CONTAINERS) {
                log.error("容器数量达到限制~");
                throw new RuntimeException("容器数量达到限制~");
            }


            //限制 该容器运行时占用的资源
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(30 * 1024 * 1024L).
                    withMemorySwap(0L).
                    withCpuCount(1L).
                    withTmpFs(ImmutableMap.of(
                            "/run", "size=10m,mode=1777"
                            // 挂载到容器的 /run，大小限制为10MB，
                            // 权限模式为1770（通常用于挂载点， owner 具有读写执行权限，group 具有读写权限，其他用户无权限）
                            // 权限模式为1777（通常用于共享内存，所有用户都具有读写执行权限）
                    ));
            //todo:hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));

            //路径映射
            //【3 WAYS: 第一种linux运行，第二种远程运行并且文件夹能同步(idea同步不了QAQ)，第三种远程运行文件夹固定】
            String userCodeDirPath = userCodeFile.getParentFile().getAbsolutePath();
            hostConfig.setBinds(new Bind(userCodeDirPath, new Volume("/app")));
            /*
            ---------------
             */
            //                        String userCodeDirPathLinux = GLOBAL_CODE_DIR_PATH_LINUX + "/" + uuidDirName;
            //                        hostConfig.setBinds(new Bind(userCodeDirPathLinux, new Volume("/app")));
            /*
            ------------------------------
             */
            //            hostConfig.setBinds(new Bind("/root/code/aurora-oj-sandbox/tmpCode/189ca6e6-4db5-463b-95cf-72c07adcc844", new Volume("/app")));

            // 创建容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(JAVA_IMAGE);
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


            //去tmp写个输入文件
            String input = String.join("\n", inputList);
/*            // 创建一个执行命令，将数据写入/tmp/file.txt
            ExecCreateCmdResponse execCreateInputCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd("sh", "-c", "echo '" + input + "' > " + INPUT_FILE_NAME)
                    .exec();
            // 执行命令
            dockerClient.execStartCmd(execCreateInputCmdResponse.getId())
                    .exec(new ResultCallback.Adapter<Frame>())
                    .awaitCompletion();
            //new ExecStartResultCallback(System.out, System.err)

            log.info("---去tmp写个输入文件----finished----");*/

            // 执行命令并获取结果

            StopWatch stopWatch = new StopWatch();
            //            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main", "<", INPUT_FILE_NAME});
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    //                    .withCmd("java -cp /app Main < " + INPUT_FILE_NAME)
                    //                    .withCmd("sh", "-c", "echo '" + input + "' > " + INPUT_FILE_NAME + " && cat " + INPUT_FILE_NAME + " | java -cp /app Main")
                    .withCmd("sh", "-c", "echo '" + input + "' > " + INPUT_FILE_NAME + " && java -cp /app Main < " + INPUT_FILE_NAME)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            //System.err.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final StringBuffer[] message = {new StringBuffer()};
            final StringBuffer[] errorMessage = {new StringBuffer()};

            long time = 0L;
            // 判断是否超时
            final boolean[] timeout = {true};

            String execId = execCreateCmdResponse.getId();

            ResultCallback.Adapter<Frame> adapter = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    log.info("----执行 - onNext----");
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0].append(new String(frame.getPayload()));
                        log.error("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0].append(new String(frame.getPayload()));
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
                    errorMessage[0].append("error:").append(throwable.getMessage());
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
            log.error("---启动检测====启动容器----");
            statsCmd.exec(statisticsResultCallback);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(adapter)
                        //限制最大执行时间
                        // .awaitCompletion();
                        .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
                adapter.close();
            } catch (InterruptedException e) {
                log.error("程序执行异常");
                throw new RuntimeException(e);
            }
            //判断是否超时？（容器设定的超时时间）
            if (timeout[0]) {
                throw new TLEException();
            }

            executeMessage.setMessage(String.valueOf(message[0]));
            executeMessage.setErrorMessage(String.valueOf(errorMessage[0]));
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);

            log.info("executeMessageList = {}", executeMessageList);
            //删除容器
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            if (e instanceof TLEException) {
                log.error("超时了");
                throw new TLEException();
            }
            log.error("程序执行异常!!!!!!!!!!!!!!!");
            throw new RuntimeException("主程序执行错误", e);
        }
        return executeMessageList;
    }


}
