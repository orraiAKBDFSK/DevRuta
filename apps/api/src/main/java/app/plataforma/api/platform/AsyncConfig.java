package app.plataforma.api.platform;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
class AsyncConfig {

	/**
	 * Hilos para enviar correo fuera de la petición HTTP: la respuesta no espera al proveedor, y el tiempo de
	 * respuesta no delata si un correo existe (recuperación de contraseña).
	 */
	@Bean(name = "mailExecutor")
	Executor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("mail-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(15);
		return executor;
	}

}
