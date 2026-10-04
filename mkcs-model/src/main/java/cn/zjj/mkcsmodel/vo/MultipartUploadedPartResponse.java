package cn.zjj.mkcsmodel.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultipartUploadedPartResponse {
    private Integer partNumber;
    private String etag;
    private Long size;
}
