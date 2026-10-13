package server;

import com.dianping.VO.CommonFollowVO;
import com.dianping.controller.FollowController;
import com.dianping.result.Result;
import com.dianping.service.FollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FollowControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FollowController controller = new FollowController();
        FollowService followService = mock(FollowService.class, invocation -> {
            if ("follow".equals(invocation.getMethod().getName())) {
                return Result.success();
            }
            if ("isFollow".equals(invocation.getMethod().getName())) {
                return Result.success(true);
            }
            if ("queryCommonFollows".equals(invocation.getMethod().getName())) {
                CommonFollowVO commonFollow = new CommonFollowVO();
                commonFollow.setId(7L);
                commonFollow.setNickName("共同关注用户");
                commonFollow.setIcon("/avatar.png");
                return Result.success(java.util.List.of(commonFollow));
            }
            return null;
        });
        ReflectionTestUtils.setField(controller, "followService", followService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void followReturnsSuccessResult() throws Exception {
        mockMvc.perform(put("/follow/2/true"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":1}"));
    }

    @Test
    void isFollowReturnsBooleanResult() throws Exception {
        mockMvc.perform(get("/follow/or/not/2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":1,\"data\":true}"));
    }

    @Test
    void commonFollowsReturnsPublicUserSummary() throws Exception {
        mockMvc.perform(get("/follow/common/2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":1,\"data\":[{\"id\":7,\"nickName\":\"共同关注用户\",\"icon\":\"/avatar.png\"}]}"));
    }
}
