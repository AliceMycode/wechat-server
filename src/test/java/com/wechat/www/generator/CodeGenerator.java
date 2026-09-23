package com.wechat.www.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 代码生成器：读取数据库表结构，生成 Entity / Mapper / Mapper.xml / Service / ServiceImpl / Controller。
 * <p>
 * 用法：改下面的「配置区」，然后直接运行 main 方法。所有输出路径均相对项目根目录。
 *
 * @author qiuzhixu
 */
public class CodeGenerator {

  // ==================== 配置区：按需修改 ====================

  /** 数据库连接：useInformationSchema=true 用于读取表/字段的中文注释 */
  private static final String DB_URL = "jdbc:mysql://localhost:3306/wechat"
      + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useInformationSchema=true";
  private static final String DB_USER = "root";
  private static final String DB_PASSWORD = "123456";

  /** 要生成的表名，留空数组表示当前库下所有表 */
  private static final String[] TABLES = {};

  /** 生成代码的基础包名 */
  private static final String BASE_PACKAGE = "com.wechat.www";

  /** 作者署名，写入类注释 */
  private static final String AUTHOR = "qiuzhixu";

  /** Java 源码输出目录 */
  private static final String JAVA_OUTPUT_DIR = "src/main/java";

  /** Mapper XML 输出目录，需与 application.yml 的 mybatis-plus.mapper-locations 对应 */
  private static final String MAPPER_XML_OUTPUT_DIR = "src/main/resources/mapper";

  // ==========================================================

  public static void main(String[] args) throws Exception {
    List<TableInfo> tables;
    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
      tables = readTables(conn);
    }

    if (tables.isEmpty()) {
      System.out.println("没有读到任何表，请检查 TABLES 配置与数据库连接。");
      return;
    }

    generateR();

    for (TableInfo table : tables) {
      String entity = table.className;
      writeJavaFile("entity", entity, entityCode(table));
      writeJavaFile("mapper", entity + "Mapper", mapperCode(table));
      writeJavaFile("service", entity + "Service", serviceCode(table));
      writeJavaFile("service.impl", entity + "ServiceImpl", serviceImplCode(table));
      writeJavaFile("controller", entity + "Controller", controllerCode(table));
      writeXmlFile(entity + "Mapper", mapperXmlCode(table));
    }

    System.out.println("完成，共生成 " + tables.size() + " 张表的代码。");
  }

  // ==================== 数据库元数据读取 ====================

  private static List<TableInfo> readTables(Connection conn) throws SQLException {
    DatabaseMetaData meta = conn.getMetaData();
    String catalog = conn.getCatalog();
    List<TableInfo> tables = new ArrayList<>();

    try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        if (!selected(tableName)) {
          continue;
        }

        TableInfo table = new TableInfo();
        table.tableName = tableName;
        table.className = toUpperCamel(tableName);
        table.comment = orDefault(rs.getString("REMARKS"), tableName);

        Set<String> primaryKeys = readPrimaryKeys(meta, catalog, tableName);
        table.columns = readColumns(meta, catalog, tableName, primaryKeys);
        tables.add(table);
      }
    }
    return tables;
  }

  private static Set<String> readPrimaryKeys(DatabaseMetaData meta, String catalog, String tableName)
      throws SQLException {
    Set<String> keys = new LinkedHashSet<>();
    try (ResultSet rs = meta.getPrimaryKeys(catalog, null, tableName)) {
      while (rs.next()) {
        keys.add(rs.getString("COLUMN_NAME"));
      }
    }
    return keys;
  }

  private static List<ColumnInfo> readColumns(DatabaseMetaData meta, String catalog, String tableName,
      Set<String> primaryKeys) throws SQLException {
    List<ColumnInfo> columns = new ArrayList<>();
    try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
      while (rs.next()) {
        ColumnInfo column = new ColumnInfo();
        column.columnName = rs.getString("COLUMN_NAME");
        column.fieldName = toLowerCamel(column.columnName);
        column.javaType = toJavaType(rs.getInt("DATA_TYPE"), rs.getInt("COLUMN_SIZE"));
        column.comment = orDefault(rs.getString("REMARKS"), column.columnName);
        column.primaryKey = primaryKeys.contains(column.columnName);
        columns.add(column);
      }
    }
    return columns;
  }

  /** MySQL/标准 JDBC 类型 -> Java 类型（全限定名，便于收集 import） */
  private static String toJavaType(int dataType, int size) {
    switch (dataType) {
      case Types.BIT:
      case Types.BOOLEAN:
        return "Boolean";
      case Types.TINYINT:
        // MySQL 的 tinyint(1) 通常被驱动识别成长度 1，语义上更接近布尔
        return size == 1 ? "Boolean" : "Integer";
      case Types.SMALLINT:
      case Types.INTEGER:
        return "Integer";
      case Types.BIGINT:
        return "Long";
      case Types.REAL:
      case Types.FLOAT:
        return "Float";
      case Types.DOUBLE:
        return "Double";
      case Types.NUMERIC:
      case Types.DECIMAL:
        return "java.math.BigDecimal";
      case Types.DATE:
        return "java.time.LocalDate";
      case Types.TIME:
        return "java.time.LocalTime";
      case Types.TIMESTAMP:
      case Types.TIMESTAMP_WITH_TIMEZONE:
        return "java.time.LocalDateTime";
      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
      case Types.BLOB:
        return "byte[]";
      default:
        return "String";
    }
  }

  // ==================== 各层代码模板 ====================

  private static String entityCode(TableInfo table) {
    Set<String> imports = new TreeSet<>();
    imports.add("com.baomidou.mybatisplus.annotation.TableName");
    imports.add("java.io.Serializable");
    imports.add("lombok.Data");
    for (ColumnInfo column : table.columns) {
      if (column.primaryKey) {
        imports.add("com.baomidou.mybatisplus.annotation.IdType");
        imports.add("com.baomidou.mybatisplus.annotation.TableId");
      }
      if (column.javaType.contains(".")) {
        imports.add(column.javaType);
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(BASE_PACKAGE).append(".entity;\n\n");
    for (String imp : imports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append('\n');
    sb.append("/**\n");
    sb.append(" * ").append(table.comment).append("\n");
    sb.append(" *\n");
    sb.append(" * @author ").append(AUTHOR).append("\n");
    sb.append(" */\n");
    sb.append("@Data\n");
    sb.append("@TableName(\"").append(table.tableName).append("\")\n");
    sb.append("public class ").append(table.className).append(" implements Serializable {\n\n");
    sb.append("  private static final long serialVersionUID = 1L;\n\n");

    for (ColumnInfo column : table.columns) {
      sb.append("  /**\n");
      sb.append("   * ").append(column.comment).append("\n");
      sb.append("   */\n");
      if (column.primaryKey) {
        sb.append("  @TableId(value = \"").append(column.columnName).append("\", type = IdType.AUTO)\n");
      }
      sb.append("  private ").append(simpleName(column.javaType)).append(' ')
          .append(column.fieldName).append(";\n\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static String mapperCode(TableInfo table) {
    return """
        package {{basePackage}}.mapper;

        import com.baomidou.mybatisplus.core.mapper.BaseMapper;
        import {{basePackage}}.entity.{{entity}};
        import org.apache.ibatis.annotations.Mapper;

        /**
         * {{comment}} Mapper
         *
         * @author {{author}}
         */
        @Mapper
        public interface {{entity}}Mapper extends BaseMapper<{{entity}}> {
        }
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{entity}}", table.className)
        .replace("{{comment}}", table.comment)
        .replace("{{author}}", AUTHOR);
  }

  private static String mapperXmlCode(TableInfo table) {
    StringBuilder columns = new StringBuilder();
    for (int i = 0; i < table.columns.size(); i++) {
      if (i > 0) {
        columns.append(", ");
      }
      columns.append(table.columns.get(i).columnName);
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
        <mapper namespace="{{basePackage}}.mapper.{{entity}}Mapper">

          <!-- 通用查询结果列 -->
          <sql id="Base_Column_List">
            {{columns}}
          </sql>

        </mapper>
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{entity}}", table.className)
        .replace("{{columns}}", columns.toString());
  }

  private static String serviceCode(TableInfo table) {
    return """
        package {{basePackage}}.service;

        import com.baomidou.mybatisplus.extension.service.IService;
        import {{basePackage}}.entity.{{entity}};

        /**
         * {{comment}} Service
         *
         * @author {{author}}
         */
        public interface {{entity}}Service extends IService<{{entity}}> {
        }
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{entity}}", table.className)
        .replace("{{comment}}", table.comment)
        .replace("{{author}}", AUTHOR);
  }

  private static String serviceImplCode(TableInfo table) {
    return """
        package {{basePackage}}.service.impl;

        import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
        import {{basePackage}}.entity.{{entity}};
        import {{basePackage}}.mapper.{{entity}}Mapper;
        import {{basePackage}}.service.{{entity}}Service;
        import org.springframework.stereotype.Service;

        /**
         * {{comment}} Service 实现
         *
         * @author {{author}}
         */
        @Service
        public class {{entity}}ServiceImpl extends ServiceImpl<{{entity}}Mapper, {{entity}}>
            implements {{entity}}Service {
        }
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{entity}}", table.className)
        .replace("{{comment}}", table.comment)
        .replace("{{author}}", AUTHOR);
  }

  private static String controllerCode(TableInfo table) {
    String entity = table.className;
    String idType = primaryKeyJavaType(table);
    return """
        package {{basePackage}}.controller;

        import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
        import {{basePackage}}.common.PageResult;
        import {{basePackage}}.common.R;
        import {{basePackage}}.entity.{{entity}};
        import {{basePackage}}.service.{{entity}}Service;
        import org.springframework.web.bind.annotation.DeleteMapping;
        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.PathVariable;
        import org.springframework.web.bind.annotation.PostMapping;
        import org.springframework.web.bind.annotation.PutMapping;
        import org.springframework.web.bind.annotation.RequestBody;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.RequestParam;
        import org.springframework.web.bind.annotation.RestController;

        import java.util.List;

        /**
         * {{comment}} 控制器
         *
         * @author {{author}}
         */
        @RestController
        @RequestMapping("/{{path}}")
        public class {{entity}}Controller {

          private final {{entity}}Service {{field}}Service;

          public {{entity}}Controller({{entity}}Service {{field}}Service) {
            this.{{field}}Service = {{field}}Service;
          }

          /** 分页列表 */
          @GetMapping("/page")
          public R<PageResult<{{entity}}>> page(
              @RequestParam(defaultValue = "1") long pageNum,
              @RequestParam(defaultValue = "10") long pageSize) {
            return R.ok(PageResult.of({{field}}Service.page(new Page<>(pageNum, pageSize))));
          }

          /** 全量列表 */
          @GetMapping("/list")
          public R<List<{{entity}}>> list() {
            return R.ok({{field}}Service.list());
          }

          /** 详情 */
          @GetMapping("/{id}")
          public R<{{entity}}> getById(@PathVariable {{idType}} id) {
            return R.ok({{field}}Service.getById(id));
          }

          /** 新增 */
          @PostMapping
          public R<Boolean> save(@RequestBody {{entity}} {{field}}) {
            return R.ok({{field}}Service.save({{field}}));
          }

          /** 修改 */
          @PutMapping
          public R<Boolean> update(@RequestBody {{entity}} {{field}}) {
            return R.ok({{field}}Service.updateById({{field}}));
          }

          /** 删除 */
          @DeleteMapping("/{id}")
          public R<Boolean> remove(@PathVariable {{idType}} id) {
            return R.ok({{field}}Service.removeById(id));
          }
        }
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{entity}}", entity)
        .replace("{{comment}}", table.comment)
        .replace("{{author}}", AUTHOR)
        .replace("{{path}}", decapitalize(entity))
        .replace("{{field}}", decapitalize(entity))
        .replace("{{idType}}", idType);
  }

  /** 统一响应体，已存在则不覆盖（避免冲掉手工改动） */
  private static void generateR() throws IOException {
    Path dir = Paths.get(JAVA_OUTPUT_DIR, (BASE_PACKAGE + ".common").replace('.', '/'));
    Path file = dir.resolve("R.java");
    if (Files.exists(file)) {
      System.out.println("已存在，跳过 " + file);
      return;
    }

    String code = """
        package {{basePackage}}.common;

        import java.io.Serializable;

        /**
         * 统一响应体
         *
         * @author {{author}}
         */
        public class R<T> implements Serializable {

          private static final long serialVersionUID = 1L;

          /** 成功 */
          public static final int CODE_SUCCESS = 200;
          /** 失败 */
          public static final int CODE_FAIL = 500;

          private int code;
          private String msg;
          private T data;

          public R() {
          }

          public R(int code, String msg, T data) {
            this.code = code;
            this.msg = msg;
            this.data = data;
          }

          public static <T> R<T> ok() {
            return new R<>(CODE_SUCCESS, "success", null);
          }

          public static <T> R<T> ok(T data) {
            return new R<>(CODE_SUCCESS, "success", data);
          }

          public static <T> R<T> fail(String msg) {
            return new R<>(CODE_FAIL, msg, null);
          }

          public static <T> R<T> fail(int code, String msg) {
            return new R<>(code, msg, null);
          }

          public int getCode() {
            return code;
          }

          public void setCode(int code) {
            this.code = code;
          }

          public String getMsg() {
            return msg;
          }

          public void setMsg(String msg) {
            this.msg = msg;
          }

          public T getData() {
            return data;
          }

          public void setData(T data) {
            this.data = data;
          }
        }
        """
        .replace("{{basePackage}}", BASE_PACKAGE)
        .replace("{{author}}", AUTHOR);

    Files.createDirectories(dir);
    Files.writeString(file, code, StandardCharsets.UTF_8);
    System.out.println("生成 " + file);
  }

  // ==================== 输出与工具方法 ====================

  private static void writeJavaFile(String subPackage, String className, String code) throws IOException {
    Path dir = Paths.get(JAVA_OUTPUT_DIR, (BASE_PACKAGE + "." + subPackage).replace('.', '/'));
    Files.createDirectories(dir);
    Path file = dir.resolve(className + ".java");
    Files.writeString(file, code, StandardCharsets.UTF_8);
    System.out.println("生成 " + file);
  }

  private static void writeXmlFile(String fileName, String code) throws IOException {
    Path dir = Paths.get(MAPPER_XML_OUTPUT_DIR);
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName + ".xml");
    Files.writeString(file, code, StandardCharsets.UTF_8);
    System.out.println("生成 " + file);
  }

  private static boolean selected(String tableName) {
    if (TABLES.length == 0) {
      return true;
    }
    for (String table : TABLES) {
      if (table.equalsIgnoreCase(tableName)) {
        return true;
      }
    }
    return false;
  }

  private static String primaryKeyJavaType(TableInfo table) {
    for (ColumnInfo column : table.columns) {
      if (column.primaryKey) {
        return simpleName(column.javaType);
      }
    }
    return "Long";
  }

  private static String simpleName(String type) {
    int index = type.lastIndexOf('.');
    return index < 0 ? type : type.substring(index + 1);
  }

  private static String orDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  /** user_name -> userName */
  private static String toLowerCamel(String name) {
    String upper = toUpperCamel(name);
    return decapitalize(upper);
  }

  /** user_name -> UserName */
  private static String toUpperCamel(String name) {
    StringBuilder sb = new StringBuilder();
    boolean upperNext = true;
    for (char c : name.toCharArray()) {
      if (c == '_') {
        upperNext = true;
        continue;
      }
      sb.append(upperNext ? Character.toUpperCase(c) : c);
      upperNext = false;
    }
    return sb.toString();
  }

  private static String decapitalize(String name) {
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  // ==================== 元数据模型 ====================

  private static class TableInfo {
    String tableName;
    String className;
    String comment;
    List<ColumnInfo> columns = new ArrayList<>();
  }

  private static class ColumnInfo {
    String columnName;
    String fieldName;
    String javaType;
    String comment;
    boolean primaryKey;
  }
}
