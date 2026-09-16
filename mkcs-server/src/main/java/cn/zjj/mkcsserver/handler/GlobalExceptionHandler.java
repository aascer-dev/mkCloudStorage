package cn.zjj.mkcsserver.handler;

import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.enumeration.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理Sa-Token未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        log.warn("未登录请求: uri={}, type={}", request.getRequestURI(), e.getType());
        return Result.error(ResultCode.UNAUTHORIZED);
    }
    
    /**
     * 处理Sa-Token权限不足异常
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e, HttpServletRequest request) {
        log.warn("权限拒绝: uri={}", request.getRequestURI());
        return Result.error(ResultCode.PERMISSION_DENIED);
    }
    
    /**
     * 处理Sa-Token角色不足异常
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e, HttpServletRequest request) {
        log.warn("角色访问被拒绝: uri={}", request.getRequestURI());
        return Result.error(ResultCode.PERMISSION_DENIED);
    }
    
    /**
     * 处理Sa-Token其他异常
     */
    @ExceptionHandler(SaTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleSaTokenException(SaTokenException e, HttpServletRequest request) {
        log.warn("Sa-Token请求失败: uri={}, exceptionType={}", request.getRequestURI(), e.getClass().getSimpleName());
        return Result.error(ResultCode.UNAUTHORIZED);
    }
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数校验异常 - @RequestBody 参数校验
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("参数校验失败: uri={}, errorCount={}", request.getRequestURI(), e.getErrorCount());
        
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errorMsg.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage()).append("; ");
        }
        
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), errorMsg.toString());
    }
    
    /**
     * 处理参数绑定异常 - @ModelAttribute 参数校验
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("参数绑定校验失败: uri={}, errorCount={}", request.getRequestURI(), e.getErrorCount());
        
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errorMsg.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage()).append("; ");
        }
        
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), errorMsg.toString());
    }
    
    /**
     * 处理约束违反异常 - @RequestParam 参数校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        log.warn("请求约束校验失败: uri={}, violationCount={}", request.getRequestURI(), e.getConstraintViolations().size());
        
        StringBuilder errorMsg = new StringBuilder();
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        for (ConstraintViolation<?> violation : violations) {
            errorMsg.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
        }
        
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), errorMsg.toString());
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型不匹配异常: {} - {}", request.getRequestURI(), e.getMessage());
        Class<?> requiredType = e.getRequiredType();
        String message = String.format("参数 '%s' 类型错误，期望类型: %s", 
            e.getName(), 
            requiredType != null ? requiredType.getSimpleName() : "未知类型");
        return Result.error(ResultCode.PARAM_INVALID.getCode(), message);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数异常: {} - {}", request.getRequestURI(), e.getMessage());
        return Result.error(ResultCode.PARAM_INVALID.getCode(), e.getMessage());
    }
    
    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        log.error("空指针异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(), "系统内部错误");
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("运行时异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(), "系统内部错误");
    }
    
    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(ResultCode.ERROR.getCode(), "系统内部错误");
    }
}
