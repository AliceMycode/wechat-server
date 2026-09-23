package com.wechat.www.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

public class SocketClient2 {

  public static void main(String[] args) {
    try (Socket socket = new Socket("127.0.0.1", 8888)) {
      PrintStream pw = new PrintStream(socket.getOutputStream(), true);

      new Thread(() -> readLoop(socket)).start();
      System.out.println("请输入要发的信息：");
      Scanner sc = new Scanner(System.in);
      while (true) {
        // 给服务端发消息
        String msg = sc.nextLine();
        pw.println(msg);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void readLoop(Socket socket) {
    // 接收服务端的消息
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String line;
      while ((line = br.readLine()) != null) {
        System.out.println(line);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
