package com.wechat.www.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

/**
 * 分页结果，作为 R 的 data 返回
 *
 * @author qiuzhixu
 */
@Data
public class PageResult<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 总记录数 */
  private long total;
  /** 当前页码 */
  private long pageNum;
  /** 每页条数 */
  private long pageSize;
  /** 总页数 */
  private long pages;
  /** 数据列表 */
  private List<T> list;

  public PageResult() {
  }

  public PageResult(long total, long pageNum, long pageSize, long pages, List<T> list) {
    this.total = total;
    this.pageNum = pageNum;
    this.pageSize = pageSize;
    this.pages = pages;
    this.list = list;
  }

  /** 由 MyBatis-Plus 的分页对象转换而来 */
  public static <T> PageResult<T> of(IPage<T> page) {
    return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getPages(),
        page.getRecords());
  }
}
