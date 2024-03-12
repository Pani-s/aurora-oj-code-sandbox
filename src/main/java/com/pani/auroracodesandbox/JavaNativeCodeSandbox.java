package com.pani.auroracodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.pani.auroracodesandbox.model.*;
import com.pani.auroracodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Pani
 * @date Created in 2024/3/9 14:28
 * @description 用爪哇java原生实现的version
 */
@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {
    /**
     * 存放用户临时代码的总目录的名字
     */
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    //发现有psf的快捷键
    /**
     * 用户代码 统一为类名叫 Main的java文件
     */
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 存放用户临时代码的总目录的path路径
     */
    private static String globalCodeDirPath;

    /**
     * 目前定死的执行最大时间 limit 不然超时
     */
    private static final long TIME_OUT = 5000L;

    /**
     * java security manager
     */
    private static final String SECURITY_MANAGER_PATH = "D:\\3Code\\00Fish\\aurora-oj\\code\\aurora-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    /**
     * 黑名单 比如使用了IO包下的类
     */
    private static final List<String> blackList = Arrays.asList("Files", "exec");

    /**
     * 字典树，用于判断code是否存在禁止词
     */
    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);

    }

    {
        //存放执行这些代码的目录，检查有没有
        //先得到用户的工作目录，tmpCode文件夹在根目录下
        String userDir = System.getProperty("user.dir");
        globalCodeDirPath = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //不同系统文件分隔符 separator 不一样
        if (!FileUtil.exist(globalCodeDirPath)) {
            FileUtil.mkdir(globalCodeDirPath);
        }
    }

    public static void main(String[] args) {
        JavaNativeCodeSandbox codeSandbox = new JavaNativeCodeSandbox();
//        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest request = ExecuteCodeRequest.builder().
                code(code).inputList(Arrays.asList("1 2", "1 3")).language("java").build();
        codeSandbox.executeCode(request);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        //        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        //  校验代码中是否包含黑名单中的命令
        //        FoundWord foundWord = WORD_TREE.matchWord(code);
        //        if (foundWord != null) {
        //            System.out.println("包含禁止词：" + foundWord.getFoundWord());
        //            return null;
        //        }


        //把用户的代码，创建文件夹隔离存放，名字的话，uuid好了
        String userCodeDirPath = globalCodeDirPath + File.separator + UUID.randomUUID();
        //用户的类 必须 要叫Main
        String userCodePath = userCodeDirPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //写进来
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //使用 Process 类在终端执行命令
        String compileCmdStr = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmdStr);
            ExecuteMessage executeMessageCompile = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            int exitValue = executeMessageCompile.getExitValue();
            String errorMessage = executeMessageCompile.getErrorMessage();
            if (exitValue != 0) {
                JudgeInfo judgeInfo = new JudgeInfo();
                judgeInfo.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getValue());
                executeCodeResponse.setJudgeInfo(judgeInfo);
                executeCodeResponse.setMessage("代码编译失败！" + errorMessage);
                log.info(executeCodeResponse.toString());
                return executeCodeResponse;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        //运行
        // 3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeDirPath, inputArgs);
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeDirPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制 ， 利用守护线程
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                //                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }

        //4. 收集整理输出结果
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }

        // 正常运行完成 相等size
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        //注意如果是答案错误，这里检测不出来的
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        // 【【【要借助第三方库来获取内存占用，非常麻烦，此处不做实现
        //        judgeInfo.setMemory();

        //5. 文件清理，该删删
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeDirPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;

    }

    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
