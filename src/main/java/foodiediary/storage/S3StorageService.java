package foodiediary.storage;

import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3 연동 목업. 실제 업로드/삭제는 구현하지 않습니다.
 * 향후 S3Config + AWS SDK 연동 시 이 클래스에 실구현을 이관할 수 있습니다.
 */
@Service
@Profile("aws")
public class S3StorageService implements StorageService {

	private static final String MESSAGE =
			"S3 연동은 목업입니다. spring.profiles.active=local 을 사용하세요.";

	@Override
	public String uploadImage(MultipartFile image) throws IOException {
		throw new UnsupportedOperationException(MESSAGE);
	}

	@Override
	public void deleteImageByUrl(String imageUrl) {
		// no-op for legacy S3 URLs when aws profile is active without real implementation
	}
}
