package foodiediary.storage;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
	String uploadImage(MultipartFile image) throws IOException;
	void deleteImageByUrl(String imageUrl);
}
