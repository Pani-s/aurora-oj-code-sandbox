package com.pani.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.pani.codesandbox.model.*;
import com.pani.codesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Pani
 * @date Created in 2024/3/12 21:06
 * @description Java 代码沙箱模板方法的实现
 * LINK：模板方法：定义一套通用的执行流程，让子类负责每个执行步骤的具体实现
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    /**
     * 自动生成的那个UUID
     */
    protected String uuidDirName;
    /**
     * 存放用户临时代码的总目录的名字
     */
    protected static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    /**
     * 用户代码 统一为类名叫 Main的java文件
     */
    protected static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 存放用户临时代码的总目录的path路径
     */
    protected static final String GLOBAL_CODE_DIR_PATH;
    /**
     * 目前定死的执行最大时间 limit 不然超时
     */
    protected static final long TIME_OUT = 5000L;

    static {
        /*
            存放执行这些代码的目录，检查有没有
         */
        //先得到用户的工作目录，tmpCode文件夹在根目录下
        String userDir = System.getProperty("user.dir");
        GLOBAL_CODE_DIR_PATH = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //不同系统文件分隔符 separator 不一样
        if (!FileUtil.exist(GLOBAL_CODE_DIR_PATH)) {
            FileUtil.mkdir(GLOBAL_CODE_DIR_PATH);
        }
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        //1. 把用户的代码保存为文件（都存放在Tmp Code 目录下）
        File userCodeFile = saveCodeToFile(code);

        //2. 编译代码，得到 class 字节码
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);
        int exitValue = compileFileExecuteMessage.getExitValue();
        String errorMessage = compileFileExecuteMessage.getErrorMessage();
        if (exitValue != 0) {
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getValue());
            judgeInfo.setDetails("代码编译失败！");
            executeCodeResponse.setJudgeInfo(judgeInfo);
            executeCodeResponse.setMessage("代码编译失败！" + errorMessage);
            executeCodeResponse.setStatus(1);
            log.info(executeCodeResponse.toString());
            return executeCodeResponse;
        }

        // 3. 执行代码，得到输出结果（子类这里不同）
        List<ExecuteMessage> executeMessageList = null;
        try{
            executeMessageList = runFile(userCodeFile, inputList);
        }catch (RuntimeException e){
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue());
            judgeInfo.setDetails("代码运行异常");
            executeCodeResponse.setJudgeInfo(judgeInfo);
            executeCodeResponse.setMessage("代码运行异常");
            executeCodeResponse.setStatus(1);
            log.info(executeCodeResponse.toString());
            return executeCodeResponse;
        }

        //4. 对比，收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        //5. 文件清理，删除
        boolean b = deleteFile(userCodeFile);
        if (!b) {
            log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }
        return outputResponse;
    }


    /**
     * 1. 把用户的代码保存为文件（都存放在Tmp Code 目录下）
     *
     * @param code
     * @return
     */
    private File saveCodeToFile(String code) {
        //把用户的代码，创建文件夹隔离存放，名字的话，uuid好了
        //该代码的临时存放目录的路径
        String userCodeDirPath = GLOBAL_CODE_DIR_PATH + File.separator + UUID.randomUUID();
        //用户的类 必须 要叫Main
        String userCodePath = userCodeDirPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //写进来
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2、编译代码
     *
     * @param userCodeFile
     * @return
     */
    private ExecuteMessage compileFile(File userCodeFile) {
        //使用 Process 类在终端执行命令
        String compileCmdStr = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmdStr);
            ExecuteMessage executeMessageCompile = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            //            int exitValue = executeMessageCompile.getExitValue();
            //            String errorMessage = executeMessageCompile.getErrorMessage();
            return executeMessageCompile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 3、执行文件，获得执行结果列表 ---由子类实现
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    abstract List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList);

    /**
     * 4、获取输出结果
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                JudgeInfo judgeInfo = new JudgeInfo();
                judgeInfo.setMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue());
                judgeInfo.setDetails(errorMessage);
                executeCodeResponse.setJudgeInfo(judgeInfo);
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(2);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        // 正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(0);
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);
            // Java 原生版本 要借助第三方库来获取内存占用，非常麻烦，此处不做实现

            judgeInfo.setMemory(maxMemory == 0 ? null : maxMemory);
            executeCodeResponse.setJudgeInfo(judgeInfo);
        }
        executeCodeResponse.setOutputList(outputList);

        return executeCodeResponse;
    }


    /**
     * 5、删除文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            //用err是因为红字注目一点 XP
            System.err.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6、获取错误响应
     *
     * @param e
     * @return
     */
    protected ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
