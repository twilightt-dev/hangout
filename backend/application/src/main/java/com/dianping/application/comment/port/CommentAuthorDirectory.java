package com.dianping.application.comment.port;

import com.dianping.application.comment.query.CommentAuthor;
import java.util.List;
import java.util.Map;

//批量查询作者昵称、头像
public interface CommentAuthorDirectory {
    Map<Long, CommentAuthor> findAuthors(List<Long> ids);
}
