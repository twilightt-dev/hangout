package com.dianping.exception;
import com.dianping.shop.cache.ShopCacheAccessException;

import com.dianping.result.Result;
import com.dianping.shop.shopdetail.exception.*;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 缓存协调失败的写操作已回滚，明确返回暂不可用。 */
    @ExceptionHandler(ShopCacheAccessException.class)
    public ResponseEntity<Result<Void>> shopCacheUnavailable(ShopCacheAccessException failure) {
        log.warn("店铺写操作缓存协调失败", failure);
        return ResponseEntity.status(503).body(Result.error("店铺暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(ShopDetailException.class)
    public ResponseEntity<Result<Void>> handleShopDetailException(ShopDetailException exception) {
        if (exception.status() >= 500) log.warn("商详查询失败", exception);
        return ResponseEntity.status(exception.status()).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(RecommendDishException.class)
    public ResponseEntity<Result<Void>> handleRecommendDishException(RecommendDishException exception) {
        return ResponseEntity.status(exception.status()).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatusException(ResponseStatusException exception) {
        if (exception.getStatusCode().is5xxServerError()) log.warn("服务暂时不可用", exception);
        return ResponseEntity.status(exception.getStatusCode()).body(Result.error(exception.getReason()));
    }

    @ExceptionHandler(VisitStatsException.class)
    public ResponseEntity<Result<Void>> handleVisitStatsException(VisitStatsException exception) {
        return ResponseEntity.status(exception.status()).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(ShopReviewException.class)
    public ResponseEntity<Result<Void>> handleShopReviewException(ShopReviewException exception) {
        return ResponseEntity.status(exception.status()).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(ShopSetmealException.class)
    public ResponseEntity<Result<Void>> handleShopSetmealException(ShopSetmealException exception) {
        return ResponseEntity.status(exception.status()).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleJwtException(JwtException exception) {
        log.debug("Refresh Token 无效或已失效: {}", exception.getMessage());
        return Result.error("登录状态已失效，请重新登录");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");
        return Result.error(message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingRequestParameterException(
            MissingServletRequestParameterException exception) {
        return Result.error("缺少请求参数：" + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        return Result.error("请求参数格式错误：" + exception.getName());
    }

    @ExceptionHandler(InvalidShopGeoQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleInvalidShopGeoQueryException(InvalidShopGeoQueryException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        log.debug("请求体无法解析: {}", exception.getMessage());
        return Result.error("请求体格式错误");
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDataAccessException(RuntimeException exception) {
        log.error("Redis 或数据库访问失败", exception);
        return Result.error("服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未处理的服务器异常", e);
        return Result.error("服务器异常");
    }
}
