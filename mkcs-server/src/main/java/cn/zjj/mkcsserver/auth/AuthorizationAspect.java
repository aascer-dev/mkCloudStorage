package cn.zjj.mkcsserver.auth;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final AuthorizationService authorizationService;

    @Around("@annotation(cn.zjj.mkcsserver.auth.RequirePermission) || @within(cn.zjj.mkcsserver.auth.RequirePermission)"
            + " || @annotation(cn.zjj.mkcsserver.auth.RequireRole) || @within(cn.zjj.mkcsserver.auth.RequireRole)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        RequirePermission permission = AnnotationUtils.findAnnotation(method, RequirePermission.class);
        if (permission == null) {
            permission = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequirePermission.class);
        }
        if (permission != null) {
            authorizationService.requirePermission(permission.value());
        }
        RequireRole role = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (role == null) {
            role = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireRole.class);
        }
        if (role != null) {
            authorizationService.requireRole(role.value());
        }
        return joinPoint.proceed();
    }
}
