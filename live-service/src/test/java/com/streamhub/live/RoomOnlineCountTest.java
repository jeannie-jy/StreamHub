package com.streamhub.live;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RoomOnlineCountTest {
    @Test
    void lightweightEndpointReturnsPresenceWithoutLoadingAnchorProfile() throws Exception {
        LiveRoomRepository rooms = mock(LiveRoomRepository.class);
        OnlinePresenceService presence = mock(OnlinePresenceService.class);
        UserServiceClient users = mock(UserServiceClient.class);
        when(rooms.findById(88)).thenReturn(Optional.of(
                new LiveRoom(88, 1, "room", null, null, "LIVE", null, null)));
        when(presence.onlineCount(88)).thenReturn(5L);
        LiveRoomService service = new LiveRoomService(rooms, presence, mock(MediaProperties.class),
                users, mock(RoomFavoriteRepository.class));
        var mvc = MockMvcBuilders.standaloneSetup(new LiveRoomController(service,
                mock(RoomDashboardService.class), mock(ActivityService.class))).build();

        mvc.perform(get("/api/v1/live/rooms/88/online-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineCount").value(5));
        verify(presence).onlineCount(88);
        verifyNoInteractions(users);
    }
}
