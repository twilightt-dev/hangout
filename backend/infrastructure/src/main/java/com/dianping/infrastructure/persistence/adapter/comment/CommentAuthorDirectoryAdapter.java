package com.dianping.infrastructure.persistence.adapter.comment;

import com.dianping.application.comment.port.CommentAuthorDirectory;
import com.dianping.application.comment.query.CommentAuthor;
import com.dianping.mapper.UserMapper;
import com.dianping.entity.User;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Repository
public class CommentAuthorDirectoryAdapter implements CommentAuthorDirectory {
    private final UserMapper mapper;

    public CommentAuthorDirectoryAdapter(UserMapper mapper) { this.mapper = mapper; }

    public Map<Long, CommentAuthor> findAuthors(List<Long> ids) {
        Map<Long, CommentAuthor> authors = new LinkedHashMap<>();
        for (User user : mapper.selectByIds(ids)) {
            authors.putIfAbsent(user.getId(), new CommentAuthor(user.getNickName(), user.getIcon()));
        }
        return authors;
    }
}
