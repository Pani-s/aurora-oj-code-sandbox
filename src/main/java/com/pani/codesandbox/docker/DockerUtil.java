package com.pani.codesandbox.docker;

import cn.hutool.core.util.ObjectUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Pani
 * @date Created in 2024/3/12 12:51
 * @description <a href="https://blog.csdn.net/weixin_45198228/article/details/130060333">...</a>
 *
 * 参考用
 */
@Slf4j
public class DockerUtil {
    public static void main(String[] args) throws InterruptedException {

        String tag = "10.20.152.16:18080/test/redis:t1";
        AuthConfig authConfig = new AuthConfig()
                .withRegistryAddress("10.20.152.16:18080")
                .withUsername("admin")
                .withPassword("admin123");

        DockerUtil dockerUtil = new DockerUtil.Builder()
                //服务器ip
                .withDockerHost("tcp://10.50.80.165:2375")
                //API版本 可通过在服务器 docker version 命令查看
                .withDockerApiVersion("1.41")
                //安全连接密钥文件存放路径
                .withDockerCertPath("/home/user/certs/")
                .build();
        //登录
        dockerUtil.login(authConfig);

        /* DockerUtil.pushImage(authConfig , tag); */
        //拉取镜像
        dockerUtil.pullImage(authConfig, tag);
        String imageId1 = dockerUtil.getImageId(tag);

        //保存镜像
        dockerUtil.saveImage(imageId1, "E:\\pdfTest\\docker\\redis.tar");
        //删除镜像
        dockerUtil.removeImage(imageId1);
    }


    private static volatile DockerClient dockerClient;

    private DockerUtil() {
    }

    private DockerUtil(String dockerHost, String dockerApiVersion, String dockerCertPath) {
        Objects.requireNonNull(dockerHost, "Docker 主机地址不能为空.");
        Objects.requireNonNull(dockerApiVersion, "Docker API 版本不能为空.");

        // 使用双重校验锁实现 Docker 客户端单例
        if (dockerClient == null) {
            synchronized (DockerUtil.class) {
                if (dockerClient == null) {
                    dockerClient = createDockerClient(dockerHost, dockerApiVersion, dockerCertPath);
                }
            }
        }
    }


    private DockerClient createDockerClient(String dockerHost, String dockerApiVersion, String dockerCertPath) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withApiVersion(dockerApiVersion)
                .withDockerHost(dockerHost)
                //如果开启安全连接，需要配置这行
                .withDockerTlsVerify(true).withDockerCertPath(dockerCertPath)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(1000)
                .connectionTimeout(Duration.ofSeconds(60))
                .responseTimeout(Duration.ofMinutes(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * 登录 Docker 镜像仓库
     *
     * @param authConfig 登录所需的认证信息
     * @throws RuntimeException 登录失败时抛出异常
     */
    public void login(AuthConfig authConfig) {
        try {
            Objects.requireNonNull(authConfig, "认证信息不能为空.");
            log.info("开始登录镜像仓库：{};username:{};password:{}", authConfig.getRegistryAddress(), authConfig.getUsername(), authConfig.getPassword());

            dockerClient.authCmd()
                    .withAuthConfig(authConfig)
                    .exec();
            log.info("镜像仓库登录成功：{}", authConfig.getRegistryAddress());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("镜像仓库登录失败：" + e.getMessage());
        }
    }

    /**
     * 从registry拉取Docker镜像
     *
     * @param tag 镜像名称
     * @return true表示拉取成功，false表示拉取失败
     */
    public void pullImage(AuthConfig authConfig, String tag) {
        Objects.requireNonNull(authConfig, "认证信息不能为空.");
        if (ObjectUtil.isEmpty(tag)) {
            throw new RuntimeException("镜像信息不能为空");
        }
        log.info("开始拉取 Docker 镜像: {}", tag);
        try {
            PullImageResultCallback exec = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("item.getStatus() = {}",item.getStatus());
                }
            };
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(tag);
            pullImageCmd.withAuthConfig(authConfig)
                    .exec(exec)
                    .awaitCompletion(30, TimeUnit.MINUTES);
            exec.close();
            log.info("镜像拉取成功：{};", tag);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("镜像拉取失败：{}" + e.getMessage());
        }
    }

    /**
     * 保存Docker镜像
     *
     * @param imageId  镜像Id
     * @param filePath 保存文件名
     * @return true表示保存成功，false表示保存失败
     */
    public void saveImage(java.lang.String imageId, String filePath) {

        if (ObjectUtil.isEmpty(filePath)) {
            throw new RuntimeException("参数错误：保存路径不能为空");
        }
        log.info("开始保存镜像：{}", imageId);
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(filePath));
             InputStream inputStream = dockerClient.saveImageCmd(imageId)
                     .exec()) {
            if (null == inputStream) {
                throw new RuntimeException("无法获取镜像");
            }
            byte[] bytesArray = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(bytesArray)) != -1) {
                outputStream.write(bytesArray, 0, bytesRead);
            }
            log.info("镜像保存成功：{}", imageId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("保存镜像异常：" + e.getMessage());
        }
    }

    /**
     * 删除Docker镜像
     *
     * @param imageId 镜像标签
     * @return true表示删除成功，false表示删除失败
     */
    public boolean removeImage(String imageId) {
        Objects.requireNonNull(imageId, "镜像 ID 不能为空.");
        log.info("开始删除 Docker 镜像: {}", imageId);
        try {
            // 如果镜像当前有容器在运行，则不进行删除操作
            if (isRunContainer(imageId)) {
                log.warn("Docker 镜像正在使用中，无法删除: {}", imageId);
                return false;
            }
            RemoveImageCmd removeImageCmd = dockerClient.removeImageCmd(imageId);
            removeImageCmd.exec();
            log.info("Docker 镜像删除成功: {}", imageId);
            return true;
        } catch (Exception e) {
            log.error("Docker 镜像删除失败: {};{}", imageId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有 Docker 容器的信息
     *
     * @return 所有 Docker 容器的信息列表
     */
    public List<Container> listContainers() {
        log.info("开始获取所有 Docker 容器信息.");
        try {
            ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
            return listContainersCmd.exec();
        } catch (Exception e) {
            log.error("获取所有 Docker 容器信息失败: {}", e.getMessage());
            throw new RuntimeException("获取所有 Docker 容器信息失败: " + e.getMessage());
        }
    }

    /**
     * 是否有在运行的容器
     *
     * @param imageId
     * @return
     */
    public boolean isRunContainer(String imageId) {
        Objects.requireNonNull(imageId, "镜像 ID 不能为空.");
        log.info("检查 Docker 镜像是否正在使用中: {}", imageId);
        try {
            List<Container> containers = listContainers();
            List<String> containerNames = containers.stream()
                    .map(Container::getImageId)
                    .collect(Collectors.toList());
            log.info("列出所有容器成功，数量：{}", containerNames.size());
            if (ObjectUtil.isNotEmpty(containerNames) && containerNames.contains(imageId)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("检查 Docker 镜像是否正在使用中失败: {}", e.getMessage());
            throw new RuntimeException("检查 Docker 镜像是否正在使用中失败: " + e.getMessage());
        }

    }

    /**
     * 推送镜像
     *
     * @param authConfig
     * @param tag
     */
    public static void pushImage(AuthConfig authConfig, String tag) {
        Objects.requireNonNull(authConfig, "认证信息不能为空.");
        Objects.requireNonNull(tag, "镜像信息不能为空.");
        log.info("开始推送 Docker 镜像: {}", tag);
        try {
            PushImageCmd pushImageCmd = dockerClient.pushImageCmd(tag);
            pushImageCmd.withAuthConfig(authConfig)
                    .start()
                    .awaitCompletion(30, TimeUnit.SECONDS);
            log.info("镜像push成功:{}", tag);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("镜像push失败：{}" + e.getMessage());
        }
    }

    /**
     * 获取镜像Id
     *
     * @param tag
     * @return
     */
    public String getImageId(String tag) {
        try {
            InspectImageCmd inspectImageCmd = dockerClient.inspectImageCmd(tag);
            InspectImageResponse image = inspectImageCmd.exec();
            if (null == image) {
                throw new RuntimeException("未获取到镜像信息：");
            }
            return image.getId();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("无法获取镜像信息：" + e.getMessage());
        }

    }

    // 使用 Builder 模式构建 DockerUtil 对象
    public static class Builder {

        private String dockerHost;
        private String dockerApiVersion;
        private String dockerCertPath;

        public Builder withDockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
            return this;
        }

        public Builder withDockerApiVersion(String dockerApiVersion) {
            this.dockerApiVersion = dockerApiVersion;
            return this;
        }

        public Builder withDockerCertPath(String dockerCertPath) {
            this.dockerCertPath = dockerCertPath;
            return this;
        }

        public DockerUtil build() {
            return new DockerUtil(dockerHost, dockerApiVersion, dockerCertPath);
        }
    }
}
