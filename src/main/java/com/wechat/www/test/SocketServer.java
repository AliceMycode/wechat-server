package com.wechat.www.test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SocketServer {

  // 在线连接表：客户端标识 -> 该连接的输出流
  private static final Map<String, PrintWriter> CLIENTS = new ConcurrentHashMap<>();

  public static void main(String[] args) {
    try (ServerSocket server = new ServerSocket(8888)) {
      while (true) {
        Socket socket = server.accept();
        new Thread(() -> handle(socket)).start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void handle(Socket socket) {
    // 用 ip:端口 做标识，同机多个客户端也能区分开
    String client = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

      // 上线：注册进在线连接表
      CLIENTS.put(client, pw);
      System.out.println(client + " 上线，当前在线 " + CLIENTS.size() + " 人");

      String line;
      while ((line = br.readLine()) != null) {
        broadcast("【" + client + "】" + line);   // 广播给所有人
      }
    } catch (Exception e) {
      System.out.println(client + " 异常断开：" + e.getMessage());
    } finally {
      // 下线：必须移除，否则表里会堆积已断开的连接
      if (CLIENTS.remove(client) != null) {
        System.out.println(client + " 下线，当前在线 " + CLIENTS.size() + " 人");
      }
    }
  }

  /**
   * 把一条消息发给所有在线客户端
   */
  private static void broadcast(String msg) {
    // System.out.println("广播：" + msg);
    for (PrintWriter pw : CLIENTS.values()) {
      pw.println(msg);
    }
  }
}
