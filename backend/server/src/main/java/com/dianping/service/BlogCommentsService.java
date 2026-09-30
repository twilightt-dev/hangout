package com.dianping.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.result.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogCommentsService extends IService<BlogComments> {

    Result<Long> createComment(Long blogId, CreateCommentDTO request);

    Result<Page<CommentVO>> queryComments(Long blogId, Integer current);

}
