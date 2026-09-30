package com.dianping.service.impl;

import com.dianping.config.UploadProperties;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.service.UploadService;
import com.dianping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UploadServiceImpl implements UploadService {
    private final StringRedisTemplate stringRedisTemplate;
    private final Path baseDirectory;

    public UploadServiceImpl(StringRedisTemplate stringRedisTemplate, UploadProperties uploadProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        Path configured = uploadProperties.getDirectory();
        if(configured == null)throw new IllegalArgumentException("上传目录未配置！")  ;
        this.baseDirectory = configured.toAbsolutePath().normalize();
    }


    /**
     * 上传图片
     * @param image
     * @return
     */
    @Override
    public Result<String> uploadBlogImage(MultipartFile image) {
        if(image == null || image.isEmpty()) {
            return Result.error("请选择图片文件！") ;
        }

        UserDTO currentUser = UserHolder.getUser() ;

        String originalFilename = image.getOriginalFilename();
        if(originalFilename == null || originalFilename.isBlank())return Result.error("图片文件名不能为空!") ;

        //提取后缀校验文件类型
        String suffix = suffixOf(originalFilename);
        if(suffix == null || !isSupported(suffix)) return Result.error("仅支持jpg、jpeg、png、gif格式") ;

        //校验请求头里的Content-Type是否为image类型
        if(image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            return Result.error("文件内容类型必须为图片!") ;
        }

        //序列化读取图片
        try{
            byte[] bytes = image.getBytes() ;
            //校验照片内容
            if(!isRealImage(bytes , suffix))return Result.error("图片内容无效!") ;

            String name = createNewFileName(suffix) ;
            Path target = safeResolve(name) ;
            //创建父目录
            Files.createDirectories(target.getParent()) ;
            //写入图片文件
            Files.write(target , bytes , StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE ) ;
            //写入redis
            try{
                stringRedisTemplate.opsForValue().set(
                        ownerKey(name) , currentUser.getId().toString() ,
                        RedisConstants.UPLOAD_OWNER_TTL , TimeUnit.HOURS
                ) ;
            }catch(RuntimeException redisFailure){
                Files.deleteIfExists(target) ;
                return Result.error("上传服务不可用，请稍后重试!") ;

            }
            log.debug("上传图片成功:{}" , name) ;
            return Result.success(name) ;

        } catch (IOException e) {
            return Result.error("上传文件失败") ;
        }

    }
    /**
     * 删除博客图片
     * @param imageName
     * @return
     */
    @Override
    public Result<String> deleteBlogImage(String imageName) {
        if(imageName == null || imageName.isEmpty()) return Result.error("文件名为空！") ;
        String relative = imageName.startsWith("/imgs/") ? imageName.substring("/imgs/".length())
                : imageName ;

        try {
            Path file = safeResolve(relative);
            UserDTO currentUser = UserHolder.getUser();
            String owner;
            try {
                owner = stringRedisTemplate.opsForValue().get(ownerKey(relative));
            } catch (RuntimeException redisFailure) {
                return Result.error("删除服务暂不可用，请稍后重试");
            }
            if (owner == null || !owner.equals(currentUser.getId().toString())) return Result.error("无权删除该图片");
            if (Files.isDirectory(file) || !Files.exists(file)) return Result.error("错误的文件名称");
            Files.delete(file);
            try {
                stringRedisTemplate.delete(ownerKey(relative));
            } catch (RuntimeException cleanupFailure) {
                log.warn("图片已删除，但所有权记录清理失败，{}", relative, cleanupFailure);
            }
            return Result.success("删除图片成功！");
        } catch (IOException | IllegalArgumentException e) {
            return Result.error("错误的文件名称");
        }
    }

    //工具方法
    //生成文件名
    private String createNewFileName(String suffix){
        String name = UUID.randomUUID().toString() ;
        int hash = name.hashCode() ;
        return String.format(Locale.ROOT , "/blogs/%x/%x/%s.%s" , hash & 0xF ,
                (hash >> 4) & 0xF , name , suffix) ;
    }
    //结果：/blogs/a/b/uuid.suffix
    //拼接路径
    private Path safeResolve(String relative){
        String clean = relative.replace('\\' , '/') ;
        while(clean.startsWith("/")){
            clean = clean.substring(1) ;
        }
        Path resolved = baseDirectory.resolve(clean).normalize() ;
        if(!resolved.startsWith(baseDirectory))throw new IllegalArgumentException("路径越界！");
        return resolved ;
    }//D:/devprojects/java-projects/comment/uploads/blogs/a/b/uuid.suffix

    //生成图片上传者的rediskey
    private String ownerKey(String relative){
        String clean = relative.replace('\\' , '/') ;
        while(clean.startsWith("/")){
            clean = clean.substring(1) ;
        }
        return RedisConstants.UPLOAD_OWNER_KEY + clean ;
    }

    //图片格式校验
    //1.提取文件扩展名
    private String suffixOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    //2.检查扩展名白名单
    private boolean isSupported(String suffix) {
        return suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("png") || suffix.equals("gif");
    }
    //3.检查文件真实内容
    private boolean isRealImage(byte[] bytes, String suffix) throws IOException {
        if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) return false;
        //下面三个分别是png、jpg、gif文件头校验
        if (suffix.equals("png")) return bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        if (suffix.equals("jpg") || suffix.equals("jpeg")) return bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
        return bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }
}
