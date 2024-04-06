package com.pani.codesandbox.example;

import java.util.Scanner;

/**
 * @author Pani
 * @date Created in 2024/3/9 14:33
 * @description 提交的代码 示例
 */
public class MainACM {
    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(2000L);
        Scanner scanner = new Scanner(System.in);
        while(scanner.hasNext()){
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            System.out.println("结果:" + (a + b));
        }
    }
}
