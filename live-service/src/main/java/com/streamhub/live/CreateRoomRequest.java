package com.streamhub.live;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 512) String coverUrl,
        @Size(max = 64) String category) {
}
