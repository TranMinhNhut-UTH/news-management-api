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
| Private | POST | `/images` | Upload multipart field `file`; trả 201 và imageUrl |
| Private | DELETE | `/images/{filename}` | Xóa file ảnh theo filename |
| Public | GET | `/uploads/{filename}` | Phục vụ ảnh đã upload |

- `GET /api/v1/news` và `GET /api/v1/news/{id}` public chỉ đọc tin tức; GET danh sách chỉ nhận `page`, `size`, mặc định mới nhất trước. `GET /api/v1/news/search` và POST/PUT/DELETE private. User chỉ sửa/xóa tin do mình viết. Không thêm vai trò admin.
- Query `GET /api/v1/news/search` yêu cầu JWT: `keyword` tìm trong title/content, `category` lọc chính xác, `sortBy` (`title` hoặc `createdAt`), `direction` (`asc`/`desc`), `page`, `size`. Mặc định `sortBy=createdAt`, `direction=desc`.
- POST/PUT News nhận `title`, `content`, `category`, `imageUrl` tùy chọn.
- Upload chỉ JPEG/PNG/WebP, tối đa 5 MB; kiểm tra định dạng ảnh, tạo filename bằng UUID và phần mở rộng phù hợp, chặn path traversal. Không tạo domain Image hoặc quản lý ownership ảnh.
- Xóa ảnh trực tiếp trên filesystem, không kiểm tra tham chiếu DB. Tin bị xóa không tự xóa file ảnh.
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
| 5. Ảnh | Upload/phục vụ/xóa file, filename UUID | Test JPEG/PNG/WebP hợp lệ, sai loại/quá 5 MB, filename UUID, đường dẫn độc hại, xóa file và file không tồn tại |
| 6. Hoàn thiện | Swagger/OpenAPI, README hướng dẫn chạy và API mẫu; tạo repository GitHub, commit/push | `mvn clean verify`; chạy luồng Register → Login → Upload → Create → Read/Search → Update → Delete News → Delete Image bằng Swagger/Postman; clone repo và chạy theo README |

Ưu tiên kiểm thử từng phase bằng Swagger/Postman; bổ sung test Spring Boot cần thiết cho validation, auth/security và nghiệp vụ chính, có thể dùng MockMvc và mock service/repository. Không yêu cầu MySQL integration test riêng. README ghi rõ quyền sửa/xóa tin, tìm kiếm và lưu ảnh để dễ trình bày với giảng viên.
