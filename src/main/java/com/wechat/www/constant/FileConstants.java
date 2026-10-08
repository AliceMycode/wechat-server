package com.wechat.www.constant;

/**
 * 文件常量：存储目录、文件后缀、文件大小、安装包
 */
public class FileConstants {

  // ============ 存储目录 ============
  public static final String FILE_FOLDER_FILE = "/file/";
  public static final String FILE_FOLDER_TEMP = "/temp/";
  public static final String FILE_FOLDER_TEMP_2 = "temp";
  public static final String FILE_FOLDER_IMAGE = "images/";
  public static final String FILE_FOLDER_AVATAR_NAME = "avatar/";

  // ============ 图片 / 后缀 ============
  public static final String IMAGE_SUFFIX = ".png";
  public static final String COVER_IMAGE_SUFFIX = "_cover.png";
  public static final String[] IMAGE_SUFFIX_LIST = {".jpeg", ".jpg", ".png", ".gif", ".bmp", ".webp"};
  public static final String[] VIDEO_SUFFIX_LIST = {".mp4", ".avi", ".rmvb", ".mkv", ".mov"};

  // ============ 文件大小 ============
  public static final Long FILE_SIZE_MB = 1024 * 1024L;   // 1 MB 的字节数

  // ============ App 安装包 ============
  public static final String APP_UPDATE_FOLDER = "/app/";
  public static final String APP_NAME = "EasyChatSetup.";
  public static final String APP_EXE_SUFFIX = ".exe";
}
