package cn.zjj.mkcsmodel.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MultipartUploadStatusResponse {
    private Long uploadId;
    private Integer partSize;
    private Integer totalParts;
    private Boolean completed;
    private Long fileId;
    private List<MultipartUploadedPartResponse> uploadedParts;
}
