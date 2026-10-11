 Nhiệm vụ 17: Mỗi nhóm (2 bạn)
- Chọn 1 ngôn ngữ bất kỳ làm BE (Backend)
- Tìm hiểu và xây dưng cấu trúc 1 dự án theo ngôn ngữ tự chọn.
- Xây dựng biến môi trường, biến toàn cục
- Xây dựng cấu trúc Request/Response
- Tạo API đăng ký/đăng nhập
- Tạo API public (đọc tin tức)
- Tạo API private (thêm xóa sửa  tìm kiếm sắp xếp tin tức)
- Tạo API upload/xóa file
* Nhớ tạo dự án trên Github
# PLAN — NV17: News Management REST API

Phạm vi: Backend REST API với hai domain `User` và `News`. Không làm frontend, không thêm refresh token, bình luận, phân quyền nhiều vai trò hoặc quản lý danh mục riêng.

## 1. Technology stack

- Java 21, Spring Boot bản stable tương thích Java 21, Maven.
- Spring Web, Spring Data JPA, Bean Validation, MySQL 8.
- Spring Security + JWT (JJWT): đăng nhập cấp access token; mật khẩu mã hóa BCrypt.
- springdoc-openapi: Swagger UI và tài liệu OpenAPI, có cấu hình Bearer JWT.
- JUnit 5, Spring Boot Test, MockMvc; Postman hoặc curl để test thủ công.
- Git/GitHub; lưu ảnh trên filesystem để triển khai bài tập đơn giản.

## 2. Project structure

```text
src/main/java/com/uth/news/
  config/       # Security, OpenAPI, cấu hình phục vụ ảnh
  constant/     # Global constants
  controller/   # Auth, News, Image
  dto/          # request/ và response/
  entity/       # User, News
  repository/   # Truy vấn dữ liệu
  service/      # Nghiệp vụ; dùng concrete class, chưa cần interface
  security/     # JWT service, filter, UserDetailsService
  exception/    # Exception và global handler
src/main/resources/application.yml
src/test/java/com/uth/news/
.env.example
README.md
PLAN.md
pom.xml
```

Controller nhận DTO → Service xử lý → Repository truy cập DB. Không trả entity trực tiếp; không trả mật khẩu/hash. Request dùng Bean Validation. Response thống nhất `{ success, message, data }`; lỗi thêm `errors` khi cần, đồng thời dùng đúng HTTP status. Danh sách phân trang trả `items`, `page`, `size`, `totalElements`, `totalPages` trong `data`.

## 3. Database schema

| Bảng | Cột và ràng buộc |
| --- | --- |
| `users` | `id BIGINT PK AUTO_INCREMENT`, `username VARCHAR(50) UNIQUE NOT NULL`, `password_hash VARCHAR(255) NOT NULL` |
| `news` | `id BIGINT PK AUTO_INCREMENT`, `title VARCHAR(255) NOT NULL`, `content LONGTEXT NOT NULL`, `category VARCHAR(100) NOT NULL`, `image_url VARCHAR(500) NULL`, `author_id BIGINT NOT NULL FK → users.id`, `created_at DATETIME NOT NULL`, `updated_at DATETIME NOT NULL` |

- Quan hệ: một User viết nhiều News; author lấy từ JWT, không nhận từ request.
- `category` là chuỗi, không tạo entity Category. Response News chứa `author: { id, username }` và trường `imageUrl`.
- Timestamps do backend tạo/cập nhật. Index cho `author_id`, `created_at` và `category`.
- Cấu hình JPA `ddl-auto=update` để tự tạo/cập nhật bảng từ entity; không cần script SQL schema riêng.

## 4. API endpoints

Prefix `/api/v1`; public không cần token, private cần `Authorization: Bearer <token>`.

| Quyền | Method | Endpoint | Chức năng |
| --- | --- | --- | --- |
| Public | POST | `/auth/register` | Tạo User từ username/password; trả 201 |
| Public | POST | `/auth/login` | Trả accessToken, tokenType, expiresIn |
| Public | GET | `/news` | Đọc danh sách tin có phân trang; mặc định mới nhất trước |
| Public | GET | `/news/{id}` | Đọc chi tiết tin |
| Private | GET | `/news/search` | Tìm kiếm, lọc, sắp xếp và phân trang; yêu cầu JWT |
| Private | POST | `/news` | Thêm tin; trả 201 |
| Private | PUT | `/news/{id}` | Sửa toàn bộ dữ liệu tin |
| Private | DELETE | `/news/{id}` | Xóa tin; trả 200 với response thống nhất |
| Private | POST | `/news/upload-image` | Upload multipart field `file`; trả 201, `data.imageUrl` |
| Private | DELETE | `/news/images/{filename}` | Xóa ảnh UUID trong UPLOAD_DIR; yêu cầu JWT; ảnh được News tham chiếu trả 409 |
| Public | GET | `/uploads/{filename}` | Phục vụ ảnh đã upload |

- `GET /api/v1/news` và `GET /api/v1/news/{id}` public chỉ đọc tin tức; GET danh sách chỉ nhận `page`, `size`, mặc định mới nhất trước. `GET /api/v1/news/search` và POST/PUT/DELETE private. User chỉ sửa/xóa tin do mình viết. Không thêm vai trò admin.
- Query `GET /api/v1/news/search` yêu cầu JWT: `keyword` tìm trong title/content, `category` lọc chính xác, `sortBy` (`title` hoặc `createdAt`), `direction` (`asc`/`desc`), `page`, `size`. Mặc định `sortBy=createdAt`, `direction=desc`.
- POST/PUT News nhận `title`, `content`, `category`, `imageUrl` tùy chọn.
- Upload chỉ JPEG/PNG/WebP, tối đa 5 MB (5 × 1024 × 1024 byte); giải mã nội dung bằng ImageIO và plugin TwelveMonkeys WebP, đối chiếu Content-Type. File rỗng trả 400, nội dung sai/giả mạo/hỏng trả 415, vượt dung lượng trả 413. Giới hạn ảnh giải mã 25 triệu pixel, vượt giới hạn trả 400 để kiểm soát bộ nhớ.
- Tạo filename bằng UUID và phần mở rộng từ định dạng thực tế; bỏ qua tên file client. Lưu trong `UPLOAD_DIR`; GET public chỉ phục vụ tên ảnh UUID hợp lệ, không phục vụ file khác hoặc symlink. URL trả về dùng `APP_BASE_URL`.
- Multipart giới hạn 5 MB/file và 6 MB/request. Tomcat đọc bỏ phần body bị từ chối để client nhận response JSON 413 thay vì lỗi kết nối.
- Không tạo domain Image hoặc quản lý ownership ảnh. Upload độc lập; client gán `data.imageUrl` vào POST/PUT News. Quyền sửa/xóa bài viết vẫn kiểm tra tác giả từ JWT. Không thay đổi schema hay dữ liệu News hiện có.
- Xóa ảnh triển khai ở Phase 6 bước 1 tại `DELETE /api/v1/news/images/{filename}`. Chỉ xóa file thường với tên UUID/đuôi ảnh hợp lệ trong `UPLOAD_DIR`; chặn traversal, symlink file và symlink trong đường dẫn thư mục. Trả 200 khi xóa thành công, 400 tên/path sai, 401 thiếu/sai JWT, 404 file không tồn tại, 409 nếu News còn tham chiếu, 500 nếu lỗi IO. Tin bị xóa không tự xóa file ảnh.
- Kiểm tra tham chiếu bằng hậu tố đường dẫn `/uploads/{filename}` của `imageUrl`, gồm URL có query/fragment và URL dùng host cũ sau khi đổi `APP_BASE_URL`. Không thêm domain/ownership ảnh. Kiểm tra DB và thao tác filesystem không phải một giao dịch nguyên tử; chưa bảo đảm chống việc gán `imageUrl` đồng thời sau thời điểm kiểm tra. URL tùy ý/biến thể percent-encoding không phải URL do API upload sinh ra.
- Lỗi chính: 400 dữ liệu sai, 401 token thiếu/sai/hết hạn, 403 không có quyền, 404 không tồn tại, 409 username trùng, 413 file quá lớn, 415 định dạng ảnh không hỗ trợ. Swagger public trong môi trường bài tập.

## 5. Environment variables và global constants

| Biến môi trường | Mục đích |
| --- | --- |
| `SERVER_PORT` | Cổng API, mặc định 8080 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối MySQL |
| `JWT_SECRET` | Khóa ký HS256, tối thiểu 32 byte; không có giá trị mặc định |
| `JWT_EXPIRATION_MS` | Thời hạn access token, mặc định 3600000 |
| `UPLOAD_DIR` | Thư mục lưu ảnh, mặc định `./uploads` |
| `APP_BASE_URL` | URL gốc để tạo imageUrl, ví dụ `http://localhost:8080` |

- `application.yml` đọc biến môi trường; `.env.example` chỉ chứa mẫu. Spring Boot không tự đọc `.env`: README hướng dẫn export biến hoặc cấu hình trong IDE.
- Global constants: `API_PREFIX`, default/max page size (10/100), allowed sort fields, image MIME types, max image size (5 MB), thông báo lỗi dùng chung. Cấu hình multipart dùng cùng giới hạn upload.
- Không đặt secret trong constants hay Git. `.gitignore` loại `.env`, `uploads/`, `target/`, file IDE và cấu hình chứa credentials.

## 6. Các phase triển khai và test

| Phase | Triển khai | Cách test / điều kiện hoàn thành |
| --- | --- | --- |
| 1. Khởi tạo | Maven, dependencies, package structure, env config, MySQL và JPA `ddl-auto=update` | Ứng dụng khởi động với DB cấu hình đúng; JPA tạo/cập nhật bảng thành công |
| 2. Nền tảng API | Entity/repository, DTO validation, response format, global exception handler | Test validation, response thành công/lỗi; xác nhận không lộ password hash |
| 3. Auth | Register/Login, BCrypt, JWT, security stateless; tắt CSRF vì dùng Bearer token, không dùng cookie auth | Test đăng ký/trùng username, login đúng/sai, token thiếu/sai/hết hạn; API public truy cập được và private trả 401 khi chưa login |
| 4. News | CRUD, author từ JWT; GET danh sách/chi tiết public, danh sách có phân trang; GET `/news/search` và POST/PUT/DELETE private | Test CRUD và 404; dùng hai User kiểm tra quyền sửa/xóa tin; test đọc public không cần token, search thiếu JWT trả 401; test keyword, category, sortBy, direction và phân trang |
| 5. Ảnh | Upload/phục vụ ảnh và liên kết `imageUrl`; filename UUID. Xóa file bổ sung ở Phase 6 bước 1 | Automated tests cho JPEG/PNG/WebP thật, file rỗng/sai/giả mạo/hỏng/quá 5 MB, JWT, đường dẫn an toàn, URL public, giới hạn multipart trên Tomcat thật và quyền cập nhật ảnh bài viết |
| 6. Hoàn thiện | Bù API xóa file còn thiếu của NV17; chuẩn hóa Swagger/OpenAPI, README, E2E MySQL thật, rà soát bảo mật và minh chứng | Theo checklist mục 8; repository GitHub đã commit/push theo thông tin người dùng. Không commit/push tiếp khi chưa được yêu cầu |

Ưu tiên kiểm thử từng phase bằng Swagger/Postman; bổ sung test Spring Boot cần thiết cho validation, auth/security và nghiệp vụ chính, có thể dùng MockMvc và mock service/repository. Không yêu cầu MySQL integration test riêng. README ghi rõ quyền sửa/xóa tin, tìm kiếm và lưu ảnh để dễ trình bày với giảng viên.

## 7. Tiến độ thực tế — 11/10/2026

- Phase 1–4 đã hoàn thành; mốc Phase 4: 52/52 tests đạt.
- Phase 5: đã triển khai upload JWT tại `POST /api/v1/news/upload-image`, phục vụ ảnh public, kiểm tra nội dung, lưu local và tests liên kết `imageUrl`. Phase 6 đã bổ sung xóa ảnh có bảo vệ tham chiếu News, chuẩn hóa Swagger/OpenAPI, hoàn thiện README và chạy E2E MySQL thật; rà soát bảo mật cuối cùng và minh chứng còn lại chưa hoàn tất.
- Các tests trước E2E dùng thư mục tạm thật, decoder thật, MockMvc và Tomcat cổng ngẫu nhiên với repository mock. Bộ E2E mới dùng repository thật, HTTP thật và SQL trên `news_management_e2e`, xác nhận `image_url`, tác giả, cập nhật timestamps và xóa bản ghi.
- `mvn clean verify` ngày 11/10/2026: BUILD SUCCESS, 69/69 tests đạt (52 tests cũ + 17 tests mới), không failures/errors/skipped.
- Phase 6 bước 1: `mvn clean verify` ngày 11/10/2026 BUILD SUCCESS, 91/91 tests đạt (69 tests trước + 22 tests xóa ảnh), không failures/errors/skipped.
- Phase 6 bước 2 Swagger/OpenAPI: `mvn clean verify` ngày 11/10/2026 BUILD SUCCESS, 97/97 tests đạt (91 tests trước + 6 tests tài liệu OpenAPI), không failures/errors/skipped. Không thay đổi business logic, security matcher hoặc HTTP status trong bước này; chưa làm README/E2E, chưa commit/push.

- Phase 6 bước 4: chạy `scripts/run-e2e.ps1` ngày 11/10/2026, `mvn clean verify -Pe2e` BUILD SUCCESS: **102 PASS (97 Surefire + 5 Failsafe E2E), 0 FAIL, 0 ERROR, 0 SKIPPED**. Đã giải mã DPAPI và xác minh database E2E trước Hibernate; cleanup chỉ dữ liệu vừa tạo và thư mục ảnh tạm. Không thay đổi business logic, chưa commit/push.

## 8. Kế hoạch Phase 6 — đối chiếu NV17 và chuẩn bị nộp bài

Trạng thái: đã triển khai bước 1 (xóa ảnh), bước 2 (Swagger/OpenAPI), bước 3 (README) và bộ bước 4 (MySQL E2E) Phase 6. Người dùng xác nhận đã commit/push Phase 1–5 lên GitHub; không cần tạo repository mới. Không commit/push thay đổi Phase 6 trong đợt này.

### 8.1. Đối chiếu yêu cầu

| Yêu cầu NV17 | Source hiện tại | Phần cần hoàn tất |
| --- | --- | --- |
| Chọn backend, cấu trúc dự án | Java 21, Spring Boot 3.5.16; Controller → Service → Repository | Giữ nguyên stack và kiến trúc; mô tả trong README |
| Biến môi trường, biến toàn cục | `application.yml`, `.env.example`, `AppConstants` | README ghi biến bắt buộc/tùy chọn, defaults và cách chạy local PowerShell 5.1 |
| Request/Response | DTO validation, `ApiResponse`, `ErrorResponse`, `PageResponse`, global/security handlers | OpenAPI mô tả đúng schema/status thành công và lỗi |
| Đăng ký/đăng nhập | BCrypt, JWT HS256, AuthController/AuthService | Xác nhận trên MySQL thật; không lộ password/hash/token trong minh chứng |
| Public đọc tin | GET danh sách/chi tiết News và ảnh public | Demo không JWT; xác nhận persistence và pagination thực tế |
| Private thêm/xóa/sửa/tìm kiếm/sắp xếp | News CRUD, JWT, ownership, keyword/category, sort whitelist, pagination | E2E với hai user và dữ liệu MySQL thật |
| Upload/xóa file | Upload JPEG/PNG/WebP, public GET và DELETE ảnh có JWT/bảo vệ tham chiếu News đã có | E2E MySQL đã xác nhận luồng PNG/gán ảnh/409/xóa; chuẩn bị minh chứng demo |
| Dự án trên GitHub | Đã commit/push theo xác nhận người dùng | Kiểm tra clone/chạy theo README và chuẩn bị link nộp bài; không tự commit/push |

### 8.2. Việc bắt buộc — thứ tự thực hiện

1. **Xóa file — đã triển khai:** endpoint chính thức theo yêu cầu mới `DELETE /api/v1/news/images/{filename}`, yêu cầu JWT; chỉ xóa tên UUID/đuôi ảnh hợp lệ trong `UPLOAD_DIR`, chặn traversal/symlink. Trả 200 với envelope hiện tại; 400 tên/path sai, 401 thiếu/sai/hết hạn JWT, 404 file không tồn tại, 409 file đang được News tham chiếu; lỗi IO trả 500 thống nhất. Tests xác nhận file thật bị xóa, GET sau xóa trả 404, tham chiếu giữ file và lỗi bảo mật không xóa file. Không thêm ownership ảnh/domain Image, không tự xóa ảnh khi xóa News. Yêu cầu mới thay thế quy ước cũ cho phép xóa file đang được tham chiếu. Tests filesystem dùng file thật; nhánh symlink/lỗi quyền IO được mô phỏng để chạy được trên Windows không có quyền tạo symlink; repository được mock, chưa E2E MySQL thật.
2. **Swagger/OpenAPI — đã triển khai:** annotations cho toàn bộ 11 operations Auth/News/Images: tags, summary, response 200/201 và các lỗi 400/401/403/404/409/413/415/500 phù hợp từng endpoint. Dùng response components lỗi chung để schema/example thống nhất; 400 có `oneOf` phân biệt envelope thường và validation có `errors`. Public routes vẫn mô tả 401 khi gửi Authorization sai; private routes kế thừa Bearer JWT. Success schemas giữ kiểu generic DTO/page; examples là JSON object `{success,message,data}`. Request DTO có examples/validation; query mô tả page/size và sort enums; upload là multipart `file` binary; GET ảnh có binary JPEG/PNG/WebP, lỗi vẫn JSON. Customizer chỉ hợp nhất schema generic được springdoc suy ra dưới `*/*` vào nội dung JSON đã khai báo, không đổi runtime negotiation/status/business logic. Tests đọc `/v3/api-docs` để kiểm tra codes, schema refs, examples, auth/public, multipart/binary và giới hạn query; đây là kiểm tra tài liệu cấp API, chưa quan sát Swagger UI bằng trình duyệt. Các mã đã khai báo khắc phục nguyên nhân `Undocumented` do thiếu response code; việc chạy demo UI/MySQL thực tế còn thuộc bước kiểm thử sau.
3. **README — đã hoàn thiện:** tài liệu tiếng Việt theo source: chức năng, stack, cấu trúc, Java 21/Maven/MySQL 8, tạo database, biến bắt buộc/tùy chọn và defaults, lệnh PowerShell 5.1, API Auth/News/Images, Swagger/Bearer, upload/đọc/gán/xóa ảnh, automated tests và giới hạn bảo mật. Cấu hình thực tế là `application.yml`, không có `application.properties`; `.env` không tự được đọc. Không chứa credentials/secret thật; chỉ sửa README và tiến độ PLAN trong bước này. `mvn clean verify` ngày 11/10/2026: BUILD SUCCESS, 97/97 tests PASS, không failures/errors/skipped. Chưa thực hiện MySQL E2E, chưa commit/push.
4. **E2E MySQL thật — đã triển khai bộ kiểm thử:** `NewsApiE2EIT` dùng production application, HTTP trên Tomcat cổng ngẫu nhiên, repository thật và JDBC SQL kiểm tra persistence. Profile Maven `e2e` chạy Failsafe; mặc định vẫn chỉ chạy tests không cần DB. `scripts/run-e2e.ps1` đọc DPAPI local-config.xml, nạp credentials/secret qua environment, đổi URL sang `news_management_e2e` và xác minh chính xác; Java kiểm tra lại URL/opt-in và `SELECT DATABASE()` trước Hibernate. Không chứa secret thật trong source/tài liệu; không in credentials và tắt logging HTTP/SQL trước Auth. Database/DPAPI lỗi thì dừng; không tự tạo database/cài MySQL. Dữ liệu user/category ngẫu nhiên, thư mục ảnh tạm; cleanup chỉ user vừa tạo và bài của họ, không truncate/xóa dữ liệu có sẵn, không kết nối database chính. Bao phủ Auth hai user, JWT, News CRUD/read/search/sort/page, PNG upload/gán/đọc, xóa ảnh 409 khi tham chiếu rồi xóa thành công/404, 400/401/403/409 và xác nhận author/URL/title/timestamps/BCrypt/bản ghi xóa bằng SQL. Pagination thử size=1 trên hai bài riêng; chưa xác nhận dataset >10 bài, restart persistence, collation/dấu, expired JWT và toàn bộ lỗi upload trên MySQL E2E (các nhánh này còn có tests không dùng DB hoặc checklist demo sau). README đã có hướng dẫn chạy và vị trí báo cáo. Không thay đổi business logic.
5. **Rà soát bảo mật trong phạm vi bài:** kiểm tra matcher search/upload/delete vẫn private; author chỉ lấy JWT, sort whitelist, DTO không lộ hash; chặn traversal/symlink khi đọc/xóa, nội dung ảnh được giải mã. Rà soát file tracked/lịch sử Git và log/minh chứng để không lộ credentials/token; nếu phát hiện secret thật đã lộ thì cần thay secret, không chỉ xóa file hiện tại. Không đổi secret đang dùng nếu không có lý do. Ghi nhận `max-swallow-size=-1` chỉ đọc bỏ body để trả 413, không nâng giới hạn file; đánh giá cấu hình này khi triển khai ngoài local.
6. **Chốt minh chứng:** chạy `mvn clean verify` sau thay đổi và ghi số tests thực tế; thử clone/chạy theo README. Chuẩn bị link GitHub, ảnh/video ngắn Swagger cho luồng chính và lỗi 401/403/404/413/415, kết quả Maven, bảng checklist E2E, sơ đồ cấu trúc/schema và đóng góp hai thành viên. Che secret, Bearer token và password hash. Chỉ đánh dấu hoàn tất khi xóa file, tài liệu, tests và E2E đều đã xác nhận; commit/push sau chỉ theo yêu cầu người dùng.

### 8.3. Cải tiến tùy chọn — không cản trở nộp bài

- Postman collection hoặc script tự chạy E2E; việc chạy và ghi nhận E2E MySQL thật vẫn bắt buộc theo mục tiêu Phase 6, nhưng không bắt buộc thêm framework/test dependency mới.
- CI GitHub Actions, Docker Compose/Testcontainers, tài liệu OpenAPI export; chỉ làm nếu còn thời gian.
- Hardening cho triển khai production (reverse proxy/TLS/rate limiting/giới hạn body bị đọc bỏ) là phần tùy chọn; không triển khai trong đợt hoàn thiện local mặc định.
- Không thêm frontend, admin/roles, refresh token, comments, Category entity, cloud storage hoặc nâng Java/dependencies chỉ để hoàn thiện bài.

### 8.4. Final Review — bước 5, ngày 11/10/2026

Đã đọc PLAN/README, cấu hình, source và tests; rà soát `git status`, các diff Phase 6 và file mới. Kiến trúc Controller → Service → Repository giữ nguyên. JWT xác minh chữ ký HS256, expiration/claims và user còn tồn tại; secret ít nhất 32 byte UTF-8, không có mặc định. Search/upload/delete private, matcher search đứng trước GET chi tiết public; ownership News lấy từ JWT. BCrypt giới hạn 72 byte, DTO validation, sort whitelist và pagination 1–100; response DTO không có password hash, lỗi JSON thống nhất. Upload kiểm tra decoder thật/MIME, giới hạn dung lượng/pixel, UUID; đọc/xóa kiểm tra tên và file symlink, xóa kiểm tra cả root/ancestor. Thư mục upload phải do ứng dụng quản lý, không cho người dùng khác sửa filesystem; race filesystem không được bảo đảm trước đối thủ có quyền ghi local. OpenAPI có tests cho 11 operations, codes/schema/examples/auth/multipart; chưa xác nhận giao diện Swagger bằng trình duyệt.

| Mức | Phát hiện / xử lý |
| --- | --- |
| Critical | Không phát hiện trong phạm vi rà soát này |
| High | Không phát hiện trong phạm vi rà soát này; không sửa business logic |
| Medium | Kiểm tra tham chiếu DB và xóa file không nguyên tử: gán ảnh đồng thời có thể tạo URL trỏ file đã xóa. Nếu cần bảo đảm đồng thời, phải thiết kế phối hợp News update và image delete; chưa thay đổi ở bước review |
| Medium | `imageUrl` tự nhập không được canonicalize; percent-encoding có thể vượt kiểm tra tham chiếu dựa trên suffix. URL do upload trả về được kiểm tra đúng. Cải tiến cần thống nhất URL hợp lệ và canonicalization, ảnh hưởng business validation nên chưa tự sửa |
| Medium | `max-swallow-size=-1` đọc bỏ body không giới hạn; cần giới hạn body/thời gian ở reverse proxy hoặc đánh giá lại Tomcat trước khi public production. Chưa thay đổi cấu hình local để giữ response 413 hiện tại |
| Low | Biến môi trường `DEBUG` đang bật khiến tests dùng token giả lập có log response Auth. Script E2E đã tắt DEBUG/TRACE trong tiến trình và phục hồi sau chạy; README nhắc không bật log HTTP/SQL chi tiết với credentials thật. Không sửa cấu hình hệ thống |
| Low | Bổ sung ignore `local-config.xml` để tránh đưa bản sao DPAPI vào Git; file thật vẫn nằm ngoài repo |
| Low | Compiler có cảnh báo unchecked trong dựng schema OpenAPI; build và tests schema đạt, không ảnh hưởng runtime business logic |

Kiểm tra bảo mật Git không in giá trị: không thấy mật khẩu/JWT secret hiện tại trong file tracked/file mới hoặc lịch sử reachable của các refs local; không thấy JWT hoàn chỉnh, private key hoặc JDBC URL chứa credentials trong lịch sử đã quét. Không có uploads/build/config nhạy cảm/dump được tracked; `.env`, uploads, target, cấu hình local và DPAPI XML được ignore. Quét file tracked/file mới không thấy email, JWT hoàn chỉnh/private key hoặc literal credentials đáng ngờ; ví dụ tài liệu và test credentials là dữ liệu giả. Đây là rà soát source/Git local, không chứng minh không có secret cũ chưa biết, remote refs chưa fetch hoặc dữ liệu đã bị chia sẻ ngoài Git; chưa làm dependency CVE audit/penetration test production.

Kết quả chạy lại: `mvn clean verify` **97 PASS, 0 FAIL/ERROR/SKIPPED**; script E2E `mvn clean verify -Pe2e` **102 PASS (97 Surefire + 5 MySQL E2E), 0 FAIL/ERROR/SKIPPED**, BUILD SUCCESS. DB được xác minh `news_management_e2e` trước Hibernate; cleanup chỉ dữ liệu vừa tạo/thư mục ảnh tạm, không kết nối database chính. Báo cáo sau lần chạy cuối được kiểm tra không chứa credentials thật hoặc token đầy đủ. `git diff --check` đạt.

**Sẵn sàng commit/push trong phạm vi bài NV17/local**, với các giới hạn Medium đã ghi rõ; chưa phải kết luận an toàn production. Chưa commit/push. Bước 6 chuẩn bị minh chứng, kiểm tra Swagger UI và clone/chạy README vẫn còn.
