package com.dianping.application.comment.port;

import com.dianping.application.comment.query.CommentPage;
import com.dianping.application.comment.query.CommentRow;


//分页读取评论
public interface CommentReadPort {
    CommentPage<CommentRow> query(Long blogId, int current);
}
