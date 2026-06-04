# 대동맛지도 — 먹기록 (Foodie Diary)

지도 기반 식사 기록·공유 모바일 서비스 **「대동맛지도 — 먹기록」**의 **Spring Boot 백엔드 API** 저장소입니다.  
클라이언트는 **Flutter** + **카카오맵 API**로 별도 개발·연동되었습니다.

| 항목 | 내용 |
|------|------|
| 개발 기간 | 2025년 4월 — 2025년 6월 |
| 과목 | 창의프로젝트 |
| 데이터 | [전국일반음식점표준데이터](https://www.data.go.kr/data/15096283/standard.do) 정제본 (저장소 포함) |

---

## 소개

일상 식사와 음식 사진이 쉽게 잊히는 문제를, **위치·사진·후기가 묶인 「먹기록」**으로 해결합니다. 공공데이터 기반 **주변 맛집(반경 1km, 최대 20곳)** 조회 후 기록을 남기고, **친구·공개 범위**에 따라 공유할 수 있습니다.

---

## 기술 스택

Java 21 · Spring Boot 3.4 · Spring Data JPA · MariaDB · JWT · **로컬 파일 저장** (기본 `local` 프로필) · GeoTools(좌표 변환) · Gradle

---

## DB 구성

**MariaDB 1개**(`foodiediary`) 안에서 테이블 역할만 나눕니다. 물리 DB를 분리하지 않습니다. **스키마와 참조 데이터는 고정**이며, 이후 변경을 전제로 하지 않습니다.

| 구분 | 테이블 | 성격 |
|------|--------|------|
| 참조(공공) | `restaurant` | 공공데이터 **정제 CSV**를 기동 시 **1회 적재**, 조회 전용 |
| 앱 | `user`, `record`, `record_image`, `friendship` | 회원·먹기록·친구, CRUD 대상 |

- **스키마**: JPA `ddl-auto: update` — 엔티티 기준으로 테이블 자동 생성·갱신
- **참조 데이터**: [`src/main/resources/data/restaurant.csv`](src/main/resources/data/restaurant.csv) (공공데이터 원본을 정제·Git 포함). `restaurant`가 비어 있으면 [`RestaurantCsvLoader`](src/main/java/foodiediary/restaurant/loader/RestaurantCsvLoader.java)가 **1회** DB에 적재
- **백업**: 포트폴리오 배포 시 앱 테이블 위주로 백업. `restaurant`는 번들 CSV 또는 DB 스냅샷으로 복원

---

## 로컬 실행

### 1. 사전 준비

- **Java 21**, **MariaDB**
- MariaDB에 빈 데이터베이스 생성 (예: `CREATE DATABASE foodiediary CHARACTER SET utf8mb4;`)

### 2. 설정 파일

| 파일 | 역할 |
|------|------|
| [`application.yml`](src/main/resources/application.yml) | DB 접속, JPA, CSV 경로 (기본 프로필: `jwt`, `local`) |
| [`application-local.yml`](src/main/resources/application-local.yml) | 이미지 저장 경로·공개 URL (`local` 프로필) |
| [`application-prod.yml`](src/main/resources/application-prod.yml) | 포트폴리오 배포용 (`show-sql: false`) |

**입력할 항목 (DB)**

| 키 | 설명 |
|----|------|
| `spring.datasource.url` | 예: `jdbc:mariadb://localhost:3306/foodiediary` |
| `spring.datasource.username` | DB 사용자 |
| `spring.datasource.password` | DB 비밀번호 |

**음식점 CSV (참조 데이터, 저장소 포함)**

| 키 | 기본값 | 설명 |
|----|--------|------|
| `app.restaurant.csv-path` | `classpath:data/restaurant.csv` | 정제 CSV 경로 (`classpath:` 또는 파일 경로) |
| `app.restaurant.batch-size` | `1000` | JDBC batch insert 크기 |

정제 CSV 파일: [`src/main/resources/data/restaurant.csv`](src/main/resources/data/restaurant.csv)

- [전국일반음식점표준데이터](https://www.data.go.kr/data/15096283/standard.do) 원본에서 **앱에 필요한 컬럼만 추출·정제**한 뒤 저장소에 포함합니다.
- 첫 기동: Hibernate가 테이블 생성 → `restaurant`가 비어 있으면 CSV **1회** 적재
- **두 번째 기동부터** 적재는 자동으로 건너뜀

CSV 파일이 아직 없으면 앱은 기동되나 WARN 로그가 출력되고 `/foodiediary/restaurant/nearby`는 빈 배열을 반환합니다. 정제 CSV를 위 경로에 추가한 뒤 DB를 비우고 재기동하면 적재됩니다.

**CSV 컬럼 (헤더명, 대소문자·공백 무시)**

| DB 컬럼 | 인식하는 헤더 예 |
|---------|------------------|
| `id` | `번호`, `관리번호`, `id` |
| `business_name` | `사업장명`, `업소명` |
| `coord_x`, `coord_y` | `좌표정보X`, `좌표정보Y` (UTM-K) |
| `full_address` | `소재지전체주소`, `도로명전체주소`, `지번주소` |

**이미 테이블이 있는 DB**

- `ddl-auto: update`로 엔티티와 스키마 차이를 자동 반영합니다.
- `restaurant`에 데이터가 이미 있으면 CSV 로더는 실행하지 않습니다.

**이미지 저장 (`local` 프로필, 기본값)**

| 키 | 기본값 | 설명 |
|----|--------|------|
| `storage.local.upload-dir` | `./data/uploads` | 업로드 파일 디렉터리 (Git 제외) |
| `storage.local.public-base-url` | `http://localhost:8080` | 클라이언트에 반환할 URL 접두사 |

- 업로드 후 URL 예: `http://localhost:8080/uploads/{uuid}_{파일명}`
- `GET /uploads/**` 로 정적 파일 제공 (JWT 불필요)

**S3 (`aws` 프로필, 목업)**  
`--spring.profiles.active=jwt,aws` 로 기동 시 이미지 API는 `UnsupportedOperationException`을 반환합니다. 실제 S3 연동은 [`S3StorageService`](src/main/java/foodiediary/storage/S3StorageService.java)에 향후 구현할 수 있습니다. [`application-aws.yml`](src/main/resources/application-aws.yml)은 placeholder만 포함합니다.

- 기본 포트: **8080**, 바인딩: `0.0.0.0`
- 기록·이미지 업로드: 요청당 최대 **10MB**, 전체 **30MB** (`spring.servlet.multipart`)

**보안:** DB 비밀번호 등을 채운 설정 파일은 **Git에 커밋하지 마세요.**

### 3. 실행

```bash
./gradlew bootRun
```

Windows: `gradlew.bat bootRun`

기동 후 기본 URL: `http://localhost:8080`

**포트폴리오 배포 예**

```bash
./gradlew bootRun --args='--spring.profiles.active=jwt,local,prod'
```

- JPA `ddl-auto: update` (엔티티 기준 테이블 생성)
- 정제 CSV는 JAR에 포함되므로 별도 파일 배치 불필요 (`classpath:data/restaurant.csv`)
- `storage.local.public-base-url`을 공개 URL로 변경

---

## 인증 (공통)

대부분의 API는 헤더가 필요합니다.

```http
Authorization: Bearer {JWT}
```

**인증 없이 호출 가능**

- `POST /foodiediary/user/login`
- `POST /foodiediary/user/signup`

로그인 성공 시 응답 JSON의 `token` 값을 이후 요청에 사용합니다.

---

## API 사용 방법

### 사용자 · 인증

#### 회원가입

```http
POST /foodiediary/user/signup
Content-Type: application/json
```

```json
{
  "id": "user01",
  "pw": "Pass1234!@",
  "name": "홍길동",
  "phoneNum": "01012345678"
}
```

| 규칙 | 내용 |
|------|------|
| id | 15자 이하, 영문+숫자 필수, `_` `.` 허용 |
| pw | 10~15자, 영문·숫자·특수문자 각 1개 이상 |
| name | 15자 이하, 한글·영문·숫자 |
| phoneNum | 11자리 숫자 |

- 성공: `200` (본문 없음)
- 실패: `400` + `{ "success": false, "message": "..." }`

#### 로그인

```http
POST /foodiediary/user/login
Content-Type: application/json
```

```json
{
  "id": "user01",
  "pw": "Pass1234!@#"
}
```

- 성공: `200` + `{ "token": "eyJ..." }`
- 실패: `401` + `"로그인 실패: ..."`

#### 내 정보 조회

```http
GET /foodiediary/user/info
Authorization: Bearer {token}
```

- 성공: `200` + User 엔티티 JSON (`id`, `pw`, `name`, `phoneNum`)

#### 내 정보 수정

```http
PATCH /foodiediary/user/info
Authorization: Bearer {token}
Content-Type: application/json
```

`id`는 토큰에서 자동 설정됩니다. 변경할 필드만 보냅니다.

```json
{
  "name": "새이름",
  "pw": "NewPass1234!@",
  "phoneNum": "01098765432"
}
```

- 성공: `200`

---

### 음식점 (공공데이터)

#### 주변 음식점 조회

현재 위치(WGS84 경위도) 기준 **반경 1km**, 거리순 **최대 20곳**.

```http
GET /foodiediary/restaurant/nearby?longitude=127.0276&latitude=37.4979
Authorization: Bearer {token}
```

| 쿼리 | 설명 |
|------|------|
| longitude | 경도 (WGS84) |
| latitude | 위도 (WGS84) |

응답 예 (`200`, 배열):

```json
[
  {
    "id": 1,
    "businessName": "○○식당",
    "longitude": 127.028,
    "latitude": 37.498,
    "fullAddress": "서울특별시 ..."
  }
]
```

---

### 먹기록

공개 범위 `visibility`: `PUBLIC` | `FRIEND` | `PRIVATE`  
이미지는 기록당 **최대 3장**.

#### 기록 작성

```http
POST /foodiediary/record/write
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

| 필드 (form) | 필수 | 설명 |
|-------------|------|------|
| title | O | 제목 |
| description | O | 설명 |
| coordinate_x | O | 위도 등 좌표 (BigDecimal) |
| coordinate_y | O | 경도 등 좌표 (BigDecimal) |
| date | O | `YYYY-MM-DD` |
| authorId | O | 작성자 user id |
| visibility | O | `PUBLIC` / `FRIEND` / `PRIVATE` |
| images | X | 이미지 파일 (복수 가능, 최대 3장) |

- 성공: `200` + 기록 ID (숫자)

#### 기록 수정

```http
PATCH /foodiediary/record/update
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

| 필드 | 필수 | 설명 |
|------|------|------|
| id | O | 기록 ID |
| title | X | |
| description | X | |
| visibility | X | |
| deleteImageUrls | X | 삭제할 이미지 URL 목록 (`/uploads/` 로컬 URL) |
| newImages | X | 추가 이미지 (합계 3장 초과 불가) |

- 성공: `200`

#### 기록 삭제

```http
DELETE /foodiediary/record/delete?id={recordId}
Authorization: Bearer {token}
```

- 성공: `200`

#### 기록 필터 조회

본인·친구 기록만 조회 가능(권한에 따라 `PRIVATE` 등 필터링).

```http
GET /foodiediary/record/list?authorId={userId}&date=2025-06-01&coordinateX=37.5&coordinateY=127.0&title=맛집&description=후기
Authorization: Bearer {token}
```

| 쿼리 | 설명 |
|------|------|
| authorId | 대상 사용자 (생략 시 본인) |
| date | `YYYY-MM-DD` |
| coordinateX, coordinateY | 위치 필터 |
| title, description | 부분 검색 |

- 성공: `200` + `RecordResponseDto[]`

#### 기록 목록 (페이지)

한 페이지 **5건**, `pageNum`은 **1부터**.

```http
GET /foodiediary/record/page?pageNum=1
Authorization: Bearer {token}
```

친구 기록:

```http
GET /foodiediary/record/page?authorId={friendId}&pageNum=1
Authorization: Bearer {token}
```

응답 필드: `id`, `title`, `description`, `coordinateX`, `coordinateY`, `date`, `author`, `visibility`, `like`, `imagePaths`

#### 기록 좋아요

```http
POST /foodiediary/record/like?recordId=1
Authorization: Bearer {token}
```

- 성공: `200` + `"좋아요 반영 성공"`
- 실패: `400` / `500`

#### 인기 공개 기록

`PUBLIC` 기록을 좋아요 순, 페이지당 5건.

```http
GET /foodiediary/record/popular?pageNum=1
Authorization: Bearer {token}
```

---

### 이미지 업로드 (로컬 저장)

기록 작성 시 `images`로 함께 보내는 방식 외, 단독 업로드 API. 기본 프로필 `local`에서 디스크에 저장합니다.

```http
POST /foodiediary/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

| 필드 | 설명 |
|------|------|
| image | 단일 파일 |

- 성공: `200` + 공개 URL 문자열 (예: `http://localhost:8080/uploads/...`)
- 조회: `GET /uploads/{fileName}` (인증 없음)

---

### 친구 (`/friends`)

모든 요청에 `Authorization: Bearer {token}` 필요.

#### 친구 요청 보내기

```http
POST /friends/request
Content-Type: application/json
```

```json
{ "targetId": "friend01" }
```

- 성공: `200` + Friendship 객체

#### 보낸 요청 취소

```http
DELETE /friends/requests/{targetId}/cancel
```

#### 요청 수락 / 거절

```http
POST /friends/requests/{requesterId}/accept
POST /friends/requests/{requesterId}/reject
```

#### 친구 삭제

```http
DELETE /friends/{friendId}
```

#### 친구 목록

```http
GET /friends
```

- 성공: `200` + `[{ "id", "otherId", "status", "createdAt" }, ...]`

#### 받은 / 보낸 요청 목록

```http
GET /friends/requests/received
GET /friends/requests/sent
```

#### 친구 추가용 사용자 검색

이미 친구이거나 본인은 제외.

```http
GET /friends/search?keyword=홍
```

- 성공: `200` + `[{ "id", "name", "phoneNum" }, ...]`

---

## API 요약표

| 메서드 | 경로 | 인증 |
|--------|------|------|
| POST | `/foodiediary/user/signup` | X |
| POST | `/foodiediary/user/login` | X |
| GET | `/foodiediary/user/info` | O |
| PATCH | `/foodiediary/user/info` | O |
| GET | `/foodiediary/restaurant/nearby` | O |
| POST | `/foodiediary/record/write` | O |
| PATCH | `/foodiediary/record/update` | O |
| DELETE | `/foodiediary/record/delete` | O |
| GET | `/foodiediary/record/list` | O |
| GET | `/foodiediary/record/page` | O |
| POST | `/foodiediary/record/like` | O |
| GET | `/foodiediary/record/popular` | O |
| POST | `/foodiediary/upload` | O |
| POST | `/friends/request` | O |
| DELETE | `/friends/requests/{targetId}/cancel` | O |
| POST | `/friends/requests/{requesterId}/accept` | O |
| POST | `/friends/requests/{requesterId}/reject` | O |
| DELETE | `/friends/{friendId}` | O |
| GET | `/friends` | O |
| GET | `/friends/requests/received` | O |
| GET | `/friends/requests/sent` | O |
| GET | `/friends/search` | O |

---

## 아키텍처

```mermaid
flowchart LR
  App[Flutter 앱]
  API[Spring Boot]
  subgraph mariadb [MariaDB foodiediary]
    Ref[restaurant 참조]
    AppTables[user record friendship]
  end
  CSV[src/main/resources/data/restaurant.csv]
  Disk[data/uploads]

  App -->|REST JWT| API
  App -->|GET uploads| API
  API --> Ref
  API --> AppTables
  API --> Disk
  CSV -->|"1회 적재"| Ref
```

---

## 공공데이터·좌표

- **원본**: [전국일반음식점표준데이터](https://www.data.go.kr/data/15096283/standard.do)
- **정제본**: [`src/main/resources/data/restaurant.csv`](src/main/resources/data/restaurant.csv) — 불필요 컬럼·행 제거 후 저장소에 포함
- **적재**: 기동 시 `restaurant` 테이블이 비어 있을 때만 자동 1회 적재
- **좌표**: CSV의 UTM-K(EPSG:5174)를 DB에 저장; API 응답 시 WGS84(EPSG:4326)로 변환 ([`CoordinateConverter`](src/main/java/foodiediary/restaurant/CoordinateConverter.java))
- **검색**: UTM-K 좌표 기준 반경 1km native query

---

## 라이선스·데이터

- 음식점 데이터: [공공데이터포털](https://www.data.go.kr/data/15096283/standard.do) 원본을 정제한 [`restaurant.csv`](src/main/resources/data/restaurant.csv) 포함 — 이용 조건 준수
- 본 저장소는 학습·포트폴리오 목적의 프로젝트 산출물입니다.
