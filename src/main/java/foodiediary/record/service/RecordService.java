package foodiediary.record.service;

import foodiediary.friendship.entity.FriendshipStatus;
import foodiediary.friendship.repository.FriendshipRepository;
import foodiediary.record.dto.RecordLikeDto;
import foodiediary.record.dto.RecordResponseDto;
import foodiediary.record.dto.RecordUpdateRequestDto;
import foodiediary.record.dto.RecordWriteRequestDto;
import foodiediary.record.entity.Record;
import foodiediary.record.entity.RecordImage;
import foodiediary.record.entity.RecordVisibility;
import foodiediary.record.repository.RecordImageRepository;
import foodiediary.record.repository.RecordRepository;
import foodiediary.storage.StorageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecordService {
    
    private final RecordRepository recordRepository;
    private final RecordImageRepository imageRepository;
    private final StorageService storageService;
    private final FriendshipRepository friendshipRepository;
    
    @Transactional
    public Long writeRecord(RecordWriteRequestDto dto) throws IOException {
        // 1. Record 저장
        Record record = Record.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .coordinateX(dto.getCoordinateX())
                .coordinateY(dto.getCoordinateY())
                .date(dto.getDate())
                .author(dto.getAuthor())
                .visibility(dto.getVisibility())
                .like(0)
                .build();
        recordRepository.save(record);
        
        // 2. 이미지 업로드 및 경로 저장
        List<MultipartFile> images = dto.getImages();
        uploadImages(images, record.getId());
        return record.getId();
    }
    
    @Transactional
    public void updateRecord(RecordUpdateRequestDto dto) throws IOException {
        Record record = recordRepository.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));
        
        // 1. 삭제할 이미지 처리
        if (dto.getDeleteImageUrls() != null && !dto.getDeleteImageUrls().isEmpty()) {
            for (String url : dto.getDeleteImageUrls()) {
                // 1) DB에서 삭제
                imageRepository.deleteByImagePath(url);
                // 2) S3에서 삭제
                storageService.deleteImageByUrl(url);
            }
        }
        
        // 2. 새 이미지 업로드 전 개수 체크
        int currentImageCount = imageRepository.countByRecordId((record.getId()));
        int newImageCount = (dto.getNewImages() != null) ? dto.getNewImages().size() : 0;
        if (currentImageCount + newImageCount > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지 등록할 수 있습니다.");
        
        // 3. 새 이미지 업로드
        if (dto.getNewImages() != null) {
            List<MultipartFile> images = dto.getNewImages();
            uploadImages(images, record.getId());
        }
        
        // 4. 기타 필드 업데이트
        if (dto.getTitle() != null) record.setTitle(dto.getTitle());
        if (dto.getDescription() != null) record.setDescription(dto.getDescription());
        if (dto.getVisibility() != null) record.setVisibility(dto.getVisibility());
        
        // dirty checking
        recordRepository.save(record);
    }
    
    @Transactional
    public void deleteRecord(Long id) {
        if (recordRepository.findById(id).isPresent())
            recordRepository.deleteRecordById(id);
    }
    
    private void uploadImages(List<MultipartFile> images, Long id) throws IOException {
        if (images != null) {
            for (MultipartFile image : images) {
                String imageUrl = storageService.uploadImage(image);
                RecordImage imagePath = RecordImage.builder()
                        .recordId(id)
                        .imagePath(imageUrl)
                        .build();
                imageRepository.save(imagePath);
            }
        }
    }

    public List<RecordResponseDto> getFilteredRecords(String loggedInUserId, String authorId,
                                                      LocalDate date, BigDecimal coordinateX,
                                                      BigDecimal coordinateY, String description, String title) {
        List<Record> records;

        boolean isOwner = (authorId == null || loggedInUserId.equals(authorId));
        boolean isFriend = false;

        if (!isOwner) {
            // 알파벳 순으로 정렬된 userId와 friendId로 검사
            String user1 = loggedInUserId.compareTo(authorId) < 0 ? loggedInUserId : authorId;
            String user2 = loggedInUserId.compareTo(authorId) < 0 ? authorId : loggedInUserId;

            isFriend = friendshipRepository.existsByUserIdAndFriendIdAndStatus(user1, user2, FriendshipStatus.ACCEPTED);
        }

        if (isOwner) {
            records = recordRepository.findFilteredRecordsForOwner(
                    loggedInUserId, title, date, coordinateX, coordinateY, description
            );
        } else if (isFriend) {
            records = recordRepository.findFilteredRecordsForFriend(
                    authorId, title, date, coordinateX, coordinateY, description
            );
        } else {
            records = List.of();
        }

        return mapToResponseDto(records);
    }


    public List<RecordResponseDto> getPagedRecords(String loggedInUserId, String authorId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber - 1, 5, Sort.by(Sort.Direction.DESC, "date"));
        Page<Record> page = null;

        boolean isOwner = (authorId == null || loggedInUserId.equals(authorId));
        boolean isFriend = false;

        if (!isOwner && authorId != null) {
            String user1 = loggedInUserId.compareTo(authorId) < 0 ? loggedInUserId : authorId;
            String user2 = loggedInUserId.compareTo(authorId) < 0 ? authorId : loggedInUserId;
            isFriend = friendshipRepository.existsByUserIdAndFriendIdAndStatus(user1, user2, FriendshipStatus.ACCEPTED);
        }

        if (isOwner) {
            page = recordRepository.findByAuthor(loggedInUserId, pageable);
        } else if (isFriend) {
            page = recordRepository.findByAuthorAndVisibilityIn(
                    authorId,
                    List.of(RecordVisibility.FRIEND, RecordVisibility.PUBLIC),
                    pageable
            );
        }

        return mapToResponseDto(page.getContent());
    }

    public void saveRecordLike(RecordLikeDto likeDto){
        Record record = recordRepository.findById(likeDto.getRecordId())
                .orElseThrow(() -> new NoSuchElementException("해당 기록을 찾을 수 없습니다."));
        record.setLike(record.getLike() + 1);
        recordRepository.save(record);
    }

    public List<RecordResponseDto> getPopularPublicRecords(int pageNum) {
        Pageable pageable = PageRequest.of(pageNum - 1, 5, Sort.by(Sort.Direction.DESC, "like"));
        Page<Record> page = recordRepository.findByVisibilityOrderByLikeDesc(RecordVisibility.PUBLIC, pageable);
        return mapToResponseDto(page.getContent());
    }

    private List<RecordResponseDto> mapToResponseDto(List<Record> records) {
        return records.stream().map(record -> {
            List<String> imagePaths = imageRepository.findByRecordId(record.getId())
                    .stream()
                    .map(RecordImage::getImagePath)
                    .collect(Collectors.toList());

            return new RecordResponseDto(
                    record.getId(),
                    record.getTitle(),
                    record.getDescription(),
                    record.getCoordinateX(),
                    record.getCoordinateY(),
                    record.getDate(),
                    record.getAuthor(),
                    record.getVisibility(),
                    record.getLike(),
                    imagePaths
            );
        }).collect(Collectors.toList());
    }
}


