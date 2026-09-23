package com.wechat.www.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class SocketClient {

  public static void main(String[] args) {
    try (Socket socket = new Socket("127.0.0.1", 8888)) {
      PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

      // 接收线程：连上就启动，整个连接期间只创建这一条
      new Thread(() -> readLoop(socket)).start();
      // 主线程：负责把键盘输入发出去
      System.out.println("请输入要发的信息：");
      Scanner sc = new Scanner(System.in);
      while (sc.hasNextLine()) {
        pw.println(sc.nextLine());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 接收线程：循环读服务端消息，直到连接断开
   */
  private static void readLoop(Socket socket) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        System.out.println(line);
      }
    } catch (Exception e) {
      System.out.println("连接已断开：" + e.getMessage());
    }
  }
}
