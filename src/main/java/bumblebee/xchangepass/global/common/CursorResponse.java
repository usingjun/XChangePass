package bumblebee.xchangepass.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "커서 기반 페이징 응답 객체")
@Builder
public record CursorResponse<T>(

        @Schema(description = "데이터 목록")
        List<T> data,

        @Schema(description = "다음 페이지 커서 (null이면 다음 페이지 없음)", example = "45")
        Long nextCursor

) {
    public static <T> CursorResponse<T> of(List<T> data, Long nextCursor) {
        return CursorResponse.<T>builder()
                .data(data)
                .nextCursor(nextCursor)
                .build();
    }
}
