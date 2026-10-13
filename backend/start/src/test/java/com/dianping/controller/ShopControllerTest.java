package com.dianping.controller;

import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopControllerTest {

    @Test
    void distanceQueryWithoutLocationIsHttpBadRequest() throws Exception {
        ShopService service = mock(ShopService.class);
        ShopController controller = new ShopController();
        controller.shopService = service;
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/shop/of/type")
                        .param("typeId", "1")
                        .param("sort", "distance")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("距离排序需要有效的经纬度"));

        verifyNoInteractions(service);
    }
}
