package foodiediary.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("local")
public class StorageWebConfig implements WebMvcConfigurer {

	private final LocalFileStorageService localFileStorageService;

	public StorageWebConfig(LocalFileStorageService localFileStorageService) {
		this.localFileStorageService = localFileStorageService;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = localFileStorageService.getUploadPath().toUri().toString();
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(location.endsWith("/") ? location : location + "/");
	}
}
