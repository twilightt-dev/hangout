package com.dianping.feed;

import com.dianping.controller.BlogController;
import com.dianping.dto.ScrollResult;
import com.dianping.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedControllerTest {
    @Test void existingFrontendRouteReturnsScrollContract() throws Exception {
        FeedService service = mock(FeedService.class);
        ScrollResult result = new ScrollResult(); result.setList(List.of()); result.setMinTime(1000L); result.setOffset(2);
        when(service.read(1000, 2)).thenReturn(Result.success(result));
        BlogController controller = new BlogController();
        ReflectionTestUtils.setField(controller, "feedService", service);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/blog/of/follow").param("lastId", "1000").param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":1,\"data\":{\"list\":[],\"minTime\":1000,\"offset\":2}}"));
    }
}
