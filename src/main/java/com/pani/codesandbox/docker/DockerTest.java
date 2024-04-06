package com.pani.codesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;

/**
 * @author Pani
 * @date Created in 2024/3/11 21:40
 * @description
 */
public class DockerTest {
//    public static void main(String[] args) throws Exception {
//        // 获取默认的 Docker Client
//        /*
//         * createDockerClient();是远程连接
//         */
////         DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        DockerClient dockerClient = createDockerClient();
//
//        //        PingCmd pingCmd = dockerClient.pingCmd();
//
//        //        pingCmd.exec();
//        // 拉取镜像
//        String image = "openjdk:8-alpine";
//
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        try {
//            pullImageCmd
//                    .exec(pullImageResultCallback)
//                    .awaitCompletion();
//        } catch (InterruptedException e) {
//            System.out.println("拉取镜像异常");
//            throw new RuntimeException(e);
//        }
//
//        System.out.println("下载完成");
//
//
//        // 拉取镜像
//        //        String image = "nginx:latest";
//        //        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        //        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//        //            @Override
//        //            public void onNext(PullResponseItem item) {
//        //                System.out.println("下载镜像：" + item.getStatus());
//        //                super.onNext(item);
//        //            }
//        //        };
//        //        pullImageCmd
//        //                .exec(pullImageResultCallback)
//        //                .awaitCompletion();
//        //        System.out.println("下载完成");
//
//
//        // 创建容器
//        //        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
//        //        CreateContainerResponse createContainerResponse = containerCmd
//        //                .withCmd("echo", "Hello Docker")
//        //                .exec();
//        //        System.out.println(createContainerResponse);
//        //        String containerId = createContainerResponse.getId();
//
//        // 查看容器状态
//        //        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
//        //        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
//        //        for (Container container : containerList) {
//        //            System.out.println(container);
//        //        }
//        //
//        //        // 启动容器
//        String containerId = "c1bbc61c2da1b669cae28eea4d9db173d0d26b062c250e3ce7c6dd0054282544";
//        //        dockerClient.startContainerCmd(containerId).exec();
//        //
//        //        //        Thread.sleep(5000L);
//        //
//        // 查看日志
//
//        //        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
//        //            @Override
//        //            public void onNext(Frame item) {
//        //                System.out.println(item.getStreamType());
//        //                System.out.println("日志：" + new String(item.getPayload()));
//        //                super.onNext(item);
//        //            }
//        //        };
//        //
//        //
//        //        dockerClient.logContainerCmd(containerId)
//        //                .withStdErr(true)
//        //                .withStdOut(true)
//        //                .exec(logContainerResultCallback)
//        //                .awaitCompletion();
//
//        // 删除容器
//        //                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
//
//        //删除镜像
//        //        dockerClient.removeImageCmd(image).exec();
//
//        /*
//        记得关闭
//         */
//        dockerClient.close();
//    }

    public static DockerClient createDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withApiVersion("1.41")
                .withDockerHost("tcp://192.168.160.129:2375")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                //                .sslConfig(config.getSSLConfig())
                //                .maxConnections(1000)
                                .connectionTimeout(Duration.ofSeconds(60))
                //                .responseTimeout(Duration.ofMinutes(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
