package com.dianping.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AvatarUpdateDTO {
    private String sourceType;
    private String uploadPath;
    private String defaultId;
}
