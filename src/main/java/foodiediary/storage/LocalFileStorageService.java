package foodiediary.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local")
public class LocalFileStorageService implements StorageService {

	private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
	private static final String UPLOAD_PATH_PREFIX = "/uploads/";

	@Value("${storage.local.upload-dir:./data/uploads}")
	private String uploadDir;

	@Value("${storage.local.public-base-url:http://localhost:8080}")
	private String publicBaseUrl;

	private Path uploadPath;

	@PostConstruct
	void init() throws IOException {
		uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
		Files.createDirectories(uploadPath);
	}

	Path getUploadPath() {
		return uploadPath;
	}

	@Override
	public String uploadImage(MultipartFile image) throws IOException {
		String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
		Path target = uploadPath.resolve(fileName);
		image.transferTo(target);
		String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
		return base + UPLOAD_PATH_PREFIX + fileName;
	}

	@Override
	public void deleteImageByUrl(String imageUrl) {
		String fileName = extractLocalFileName(imageUrl);
		if (fileName == null) {
			log.debug("Skipping non-local image URL: {}", imageUrl);
			return;
		}
		try {
			Files.deleteIfExists(uploadPath.resolve(fileName));
		} catch (IOException e) {
			log.warn("Failed to delete local image {}: {}", fileName, e.getMessage());
		}
	}

	private String extractLocalFileName(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return null;
		}
		int idx = imageUrl.indexOf(UPLOAD_PATH_PREFIX);
		if (idx < 0) {
			return null;
		}
		return imageUrl.substring(idx + UPLOAD_PATH_PREFIX.length());
	}
}
