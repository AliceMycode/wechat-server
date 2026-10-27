package com.wechat.www.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 通用分页返回（前端 v-infinite-scroll 只认 pageTotal/pageNo/list）
 * 其它项目接口以后分页也用这个
 */
@Data
public class PaginationResultVO<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private Integer pageNo;      // 当前页码（从1开始）
  private Integer pageSize;    // 每页条数
  private Integer pageTotal;   // 总页数
  private Long total;          // 总条数
  private List<T> list;        // 当前页数据

  /** 构造时自动算总页数 */
  public PaginationResultVO(Integer pageNo, Integer pageSize, Long total, List<T> list) {
    this.pageNo = pageNo;
    this.pageSize = pageSize;
    this.total = total;
    this.list = list;
    // 总页数 = 向上取整（10条数据每页5条 → 2页；11条 → 3页）
    this.pageTotal = (int) Math.ceil((double) total / pageSize);
  }
}
