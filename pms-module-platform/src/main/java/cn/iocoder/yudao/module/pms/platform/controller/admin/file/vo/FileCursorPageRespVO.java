package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCursorPageRespVO<T> {
    private List<T> items;
    private String nextCursor;
    private Boolean hasMore;
}
