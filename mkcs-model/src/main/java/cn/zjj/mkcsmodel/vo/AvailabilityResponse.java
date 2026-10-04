package cn.zjj.mkcsmodel.vo;

import io.micrometer.common.lang.Nullable;

public record AvailabilityResponse(
        boolean available,
        @Nullable String reason   // 只有不可用时才有值，可选放更多信息如“已被用户xxx占用”
    ) {

}