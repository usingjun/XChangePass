package bumblebee.xchangepass.domain.aspect;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class ApiCallAspect {

    @Autowired
    private MeterRegistry meterRegistry;  // 메트릭스를 기록할 MeterRegistry

    @Around("execution(* bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService.*(..))")
    public Object logApiMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        // Windows에서도 CPU 사용량을 가져올 수 있도록 `com.sun.management.OperatingSystemMXBean` 사용
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // API 실행 전 CPU 사용량 (JVM 프로세스 기준)
        double cpuBefore = osBean.getProcessCpuLoad() * 100;  // 백분율 변환
        long startTime = System.currentTimeMillis();
        System.out.println("START: " + joinPoint.toString());
        // 외부 API 호출 진행
        Object result = joinPoint.proceed();

        // API 실행 후 CPU 사용량 및 실행 시간 측정
        long endTime = System.currentTimeMillis();
        double cpuAfter = osBean.getProcessCpuLoad() * 100;  // 백분율 변환
        long duration = endTime - startTime;

        String methodName = joinPoint.getSignature().getName();
        // Micrometer 메트릭스에 기록
        meterRegistry.timer("api.call.duration", "api", methodName).record(duration, TimeUnit.MILLISECONDS);

        // 로그 출력 (실시간 CPU 사용률 확인용)
        System.out.println("API call duration: " + duration + "ms");
        System.out.println("CPU Before API Call: " + String.format("%.2f", cpuBefore) + "%");
        System.out.println("CPU After API Call: " + String.format("%.2f", cpuAfter) + "%");

        return result;
    }
}
