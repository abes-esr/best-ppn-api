package fr.abes.bestppn.utils;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Arrays;

import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Aspect
@Slf4j
public class ExecutionTimeAspect {

    @Around("@annotation(ExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        double executionTime = (double) (endTime - startTime) / 1000;

        log.debug(TECHNICAL, "------------------------------------------------------");
        log.debug(TECHNICAL, "Classe : " + joinPoint.getSignature().getDeclaringTypeName());
        log.debug(TECHNICAL, "Méthode : " + joinPoint.getSignature().getName());
        log.debug(TECHNICAL, "Paramètres : " + Arrays.toString(joinPoint.getArgs()));
        log.debug(TECHNICAL, "Temps d'exécution : " + executionTime + " secondes");
        log.debug(TECHNICAL, "------------------------------------------------------");

        return result;
    }
}
