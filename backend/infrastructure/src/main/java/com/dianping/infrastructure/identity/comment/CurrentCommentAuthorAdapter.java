package com.dianping.infrastructure.identity.comment;

import com.dianping.application.comment.port.CurrentCommentAuthor;
import com.dianping.dto.UserDTO;
import com.dianping.utils.UserHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentCommentAuthorAdapter implements CurrentCommentAuthor {
    public Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? null : user.getId();
    }
}
