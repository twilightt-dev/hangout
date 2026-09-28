package com.dianping.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/blog-comments")
@Tag(name = "博客评论接口")
public class BlogCommentsController {

}
