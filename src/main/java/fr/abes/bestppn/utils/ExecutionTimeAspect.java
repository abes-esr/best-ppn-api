package fr.abes.bestppn.utils;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Arrays;

@Aspect
@Slf4j
public class ExecutionTimeAspect {

    @Around("@annotation(ExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        double executionTime = (endTime - startTime) / 1000;

        log.debug("------------------------------------------------------");
        log.debug("Classe : " + joinPoint.getSignature().getDeclaringTypeName());
        log.debug("Méthode : " + joinPoint.getSignature().getName());
        log.debug("Paramètres : " + Arrays.toString(joinPoint.getArgs()));
        log.debug("Temps d'exécution : " + executionTime + " secondes");
        log.debug("------------------------------------------------------");

        return result;
    }
}
