# News Management REST API — NV17

Backend quản lý tin tức theo kiến trúc **Controller → Service → Repository**. Đã triển khai đăng ký/đăng nhập JWT, đọc tin công khai, CRUD theo quyền tác giả, tìm kiếm/lọc/sắp xếp/phân trang, upload/đọc/xóa ảnh local và Swagger/OpenAPI.

Phase 6 đã có API xóa ảnh, OpenAPI, README, E2E MySQL thật và rà soát cuối trong phạm vi NV17. Minh chứng nộp bài còn cần chuẩn bị; xem [PLAN.md](PLAN.md).

## Công nghệ và môi trường

- **JDK 21**, **Maven 3.6.3+**, **MySQL 8**.
- Spring Boot **3.5.16**: Web, Data JPA, Bean Validation, Security.
- JJWT **0.13.0** (JWT HS256), BCrypt; springdoc-openapi **2.8.17**.
- TwelveMonkeys ImageIO WebP **3.15.0**; JUnit 5, Mockito, MockMvc và Tomcat trong tests.

```powershell
java -version
mvn -version
```

Maven phải dùng Java 21. Không cần frontend để chạy API.

## Cấu trúc chính

```text
src/main/java/com/uth/news/
  config/       # Security, OpenAPI
  constant/     # Prefix, giới hạn phân trang/ảnh, sort whitelist
  controller/   # Auth, News, upload/đọc/xóa ảnh
  dto/          # Request validation, response
  entity/       # User, News
  repository/   # Truy vấn JPA
  service/      # Nghiệp vụ, lưu/xóa ảnh
  security/     # JWT, filter, lỗi security
  exception/    # Exception, global handler
src/main/resources/application.yml
src/test/java/com/uth/news/
.env.example
pom.xml
PLAN.md
```

Cấu hình hiện tại dùng `application.yml`; không có `application.properties`.

## Khởi động local

### 1. Chuẩn bị MySQL

Khởi động MySQL 8, mở Workbench hoặc MySQL CLI và chạy:

```sql
CREATE DATABASE IF NOT EXISTS news_management
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Dùng tài khoản MySQL local có quyền đọc/ghi và tạo/cập nhật bảng trong database này. Có thể tạo tài khoản riêng bằng Workbench; không lưu mật khẩu vào source. JPA `ddl-auto=update` tạo/cập nhật bảng `users`, `news`, không tự tạo database. `News.author_id` tham chiếu `User.id`; `image_url` có thể null.

### 2. Cấu hình và chạy

| Biến môi trường | Bắt buộc | Mặc định / ý nghĩa |
| --- | --- | --- |
| `DB_URL` | Có | JDBC URL MySQL; không có mặc định |
| `DB_USERNAME` | Có | Tài khoản MySQL; không có mặc định |
| `DB_PASSWORD` | Có | Mật khẩu MySQL; không có mặc định |
| `JWT_SECRET` | Có | Không rỗng, ít nhất 32 byte UTF-8; không có mặc định |
| `SERVER_PORT` | Không | `8080` |
| `JWT_EXPIRATION_MS` | Không | `3600000` (1 giờ), tối thiểu `1000` |
| `UPLOAD_DIR` | Không | `./uploads`, tính từ thư mục chạy |
| `APP_BASE_URL` | Không | `http://localhost:8080`, dùng tạo URL ảnh |

`.env.example` chỉ là mẫu; **Spring Boot không tự đọc `.env`**. Thiết lập biến trong terminal chạy Maven hoặc Environment Variables của cấu hình chạy IDE.

Ví dụ **Windows PowerShell 5.1**, chạy từ thư mục chứa `pom.xml`:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/news_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'
$env:DB_USERNAME = Read-Host 'MySQL username'
$dbPassword = Read-Host 'MySQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password

# Sinh secret local ngẫu nhiên; không in ra terminal.
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

mvn spring-boot:run
```

JDBC URL trên dành cho local. Secret được dùng trực tiếp dưới dạng UTF-8, không decode Base64. Giữ cùng secret giữa các lần chạy nếu muốn token cũ hợp lệ đến khi hết hạn; sinh secret mới sẽ vô hiệu token cũ. Có thể lưu cấu hình ngoài repo bằng CLIXML với password/secret dạng `SecureString` trên Windows rồi nạp vào môi trường; không commit script chứa secret plaintext.

Nếu đổi `SERVER_PORT`, đặt cả `APP_BASE_URL` theo địa chỉ/cổng truy cập thực tế. `UPLOAD_DIR` cần quyền ghi và nên là thư mục riêng, không dùng symlink; ứng dụng tạo thư mục khi upload.

## Danh sách API

Đường dẫn dưới đây là đường dẫn đầy đủ. API private yêu cầu `Authorization: Bearer <accessToken>`.

### Auth

| Method | Endpoint | Quyền | Thành công |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201, `data.id`, `data.username` |
| POST | `/api/v1/auth/login` | Public | 200, `data.accessToken`, `data.tokenType`, `data.expiresIn` |

Body gồm `username`, `password`. Username không rỗng, tối đa 50 ký tự; password đăng ký 8–72 ký tự và tối đa 72 byte UTF-8. Username trùng trả 409; đăng nhập sai trả 401.

### News

| Method | Endpoint | Quyền | Chức năng / thành công |
| --- | --- | --- | --- |
| GET | `/api/v1/news` | Public | 200, danh sách phân trang, mới nhất trước |
| GET | `/api/v1/news/{id}` | Public | 200, chi tiết |
| GET | `/api/v1/news/search` | JWT | 200, tìm kiếm/lọc/sắp xếp/phân trang |
| POST | `/api/v1/news` | JWT | 201, tạo bài, author lấy từ JWT |
| PUT | `/api/v1/news/{id}` | JWT, tác giả | 200, cập nhật toàn bộ các trường request |
| DELETE | `/api/v1/news/{id}` | JWT, tác giả | 200, xóa bài |

POST/PUT nhận `title` (không rỗng, tối đa 255), `content` (không rỗng), `category` (không rỗng, tối đa 100), `imageUrl` (tùy chọn, tối đa 500). Không nhận author từ client. PUT với `imageUrl=null` bỏ liên kết ảnh. Người khác sửa/xóa bài nhận 403.

GET danh sách nhận `page`, `size`: page bắt đầu từ **0**, size mặc định **10**, từ **1 đến 100**. `data` chứa `items`, `page`, `size`, `totalElements`, `totalPages`. Bài viết trả `author: {id, username}`, `imageUrl`, `createdAt`, `updatedAt`; không trả entity hoặc password hash.

Search nhận `keyword` trong title/content, `category` theo giá trị, `sortBy=title|createdAt`, `direction=asc|desc`, `page`, `size`. Mặc định `createdAt`, `desc`; bộ lọc bỏ trống không giới hạn kết quả. `%`/`_` trong keyword là ký tự thường; so sánh dữ liệu còn phụ thuộc collation MySQL.

```text
/api/v1/news/search?keyword=Spring&category=Tech&sortBy=title&direction=asc&page=0&size=10
```

### Upload / đọc / xóa ảnh

| Method | Endpoint | Quyền | Thành công |
| --- | --- | --- | --- |
| POST | `/api/v1/news/upload-image` | JWT | 201, `data.imageUrl` |
| GET | `/uploads/{filename}` | Public | 200, dữ liệu JPEG/PNG/WebP |
| DELETE | `/api/v1/news/images/{filename}` | JWT | 200 nếu ảnh không được News tham chiếu |

Upload dùng **multipart/form-data**, field **`file`**. Chỉ JPEG/PNG/WebP, tối đa **5 MB (5 × 1024 × 1024 byte)** mỗi file, **6 MB** mỗi request, tối đa **25 triệu pixel**. Backend giải mã nội dung và đối chiếu Content-Type. File rỗng/quá giới hạn pixel trả 400, sai/giả mạo/hỏng trả 415, quá dung lượng trả 413.

Ảnh lưu local bằng tên UUID và đuôi theo định dạng thực tế; bỏ qua tên file client. Luồng sử dụng:

1. Upload và lấy `data.imageUrl`; mở URL đó không cần JWT.
2. Gán nguyên URL vào `imageUrl` khi POST/PUT News. Upload không tự tạo/cập nhật bài.
3. Muốn xóa ảnh: tác giả bỏ/thay URL ở **mọi bài đang tham chiếu** hoặc xóa các bài đó trước, rồi gọi DELETE ảnh bằng `filename` trong URL.
4. Còn bài tham chiếu: 409 và giữ file. Xóa thành công: 200 với `data=null`; GET/xóa lại file nhận 404.

Xóa bài **không tự xóa ảnh**. API xóa ảnh chặn tên sai, traversal và symlink; chỉ thao tác tên UUID `.jpg`, `.png`, `.webp` trong `UPLOAD_DIR`.

## Swagger UI và response

Sau khi ứng dụng khởi động:

- Swagger UI: `http://localhost:8080/swagger-ui.html`.
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

Register → Login → lấy `data.accessToken` → **Authorize** → dán token **không thêm chữ `Bearer`**. Swagger tự tạo header Bearer. Upload: **Try it out** → chọn `file` → **Execute**. Với Postman/curl, gửi header `Authorization: Bearer <accessToken>` cho API private.

Public endpoint không cần JWT; nếu gửi Authorization sai/hết hạn thì filter vẫn trả 401. Bỏ token cũ khi đăng ký/đăng nhập hoặc thử đọc public.

Các API JSON dùng envelope chung; GET ảnh trả bytes, không dùng envelope thành công:

```json
{"success": true, "message": "Image deleted successfully", "data": null}
```

```json
{"success": false, "message": "Image is referenced by a news article", "data": null}
```

Validation có thêm `errors` dạng map field → danh sách thông báo. OpenAPI mô tả schemas, examples và response codes theo từng operation.

| HTTP | Ý nghĩa lỗi |
| --- | --- |
| 400 | Body/query/path sai, file rỗng hoặc giới hạn pixel |
| 401 | JWT thiếu/sai/hết hạn, credentials sai |
| 403 | Không phải tác giả khi sửa/xóa bài |
| 404 | Bài viết/ảnh không tồn tại |
| 409 | Username trùng, constraint DB hoặc ảnh đang được sử dụng |
| 413 | File hoặc multipart quá dung lượng |
| 415 | Content-Type không hỗ trợ hoặc nội dung ảnh không hợp lệ |
| 500 | Lỗi server/DB/filesystem; response không lộ chi tiết nội bộ |

## Automated tests

Từ thư mục chứa `pom.xml`:

```powershell
mvn clean verify
```

Mốc gần nhất: **97/97 tests PASS**, không failures/errors/skipped. Tests bao phủ DTO/error response, BCrypt/JWT, public/private, ownership, News CRUD/search/sort/page, upload/đọc/xóa ảnh và OpenAPI. Có file ảnh/thư mục tạm và Tomcat thật; repository được mock. Nhánh symlink/lỗi quyền IO được mô phỏng để chạy trên Windows không có quyền tạo symlink.

Lệnh mặc định chạy 97 tests trên, không cần MySQL hoặc DB/secret local. Bộ E2E bên dưới chạy riêng với repository/HTTP/MySQL thật. Build thành công tạo JAR trong `target/`.

### E2E với MySQL thật

Điều kiện: MySQL 8 hoạt động, database riêng **`news_management_e2e`** đã tồn tại và tài khoản có quyền đọc/ghi/tạo/cập nhật bảng tại đó. Cấu hình `%LOCALAPPDATA%\NewsManagementApi\local-config.xml` phải được tạo bằng `Export-Clixml` bởi cùng tài khoản Windows trên cùng máy: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và `JWT_SECRET`; hai giá trị nhạy cảm là `SecureString` được DPAPI bảo vệ. Không gửi hoặc đưa secret vào Git.

Từ thư mục repo, chạy bằng Windows PowerShell 5.1:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-e2e.ps1
```

`Bypass` chỉ áp dụng cho tiến trình này, không đổi execution policy hệ thống. Script nạp DPAPI, thay tên database `news_management` trong JDBC URL thành `news_management_e2e`, xác minh tên chính xác, rồi chạy **`mvn clean verify -Pe2e`**. Không dùng URL database chính để chạy E2E. Để chạy Maven trực tiếp, cần các biến `DB_*`, `JWT_SECRET` hợp lệ trỏ E2E và `NEWS_E2E_ENABLED=true`; script là cách khuyến nghị để tránh cấu hình nhầm.

Bộ `NewsApiE2EIT` kiểm tra thêm `SELECT DATABASE()` trước khi Hibernate khởi động; URL sai, DPAPI lỗi hoặc MySQL không kết nối được sẽ dừng với lỗi, không đánh dấu PASS/SKIPPED. Spring Boot chạy Tomcat ở cổng ngẫu nhiên, repository thật, thư mục upload tạm riêng; tests không dùng mock. Các luồng:

- Auth hai user, JWT, BCrypt lưu trong MySQL, username trùng/credentials sai.
- Upload PNG, đọc ảnh public, tạo bài có ảnh và xác nhận author/URL/timestamps bằng SQL.
- Read/search/sort/page thực tế; lỗi 401/403 và kiểm tra bài không bị người khác thay đổi.
- Chặn xóa ảnh đang được bài tham chiếu với 409; bỏ URL, xác nhận cập nhật SQL rồi xóa ảnh và kiểm tra 404.
- Xóa bài, xác nhận bản ghi đã mất trong MySQL và validation/path sai trả 400.

Mỗi lần chạy dùng username/category ngẫu nhiên; chỉ dọn bài thuộc các user vừa tạo và chính các user đó, không truncate bảng hoặc xóa dữ liệu có sẵn. Không kết nối database `news_management`. Script phục hồi biến môi trường sau chạy; logging HTTP/SQL bị tắt trước Auth để token/password không vào báo cáo. Nếu tiến trình bị kill, dữ liệu test có thể còn lại trong E2E; lần chạy tiếp dùng tên mới, không tự xóa dữ liệu của lần trước.

Kết quả Maven tách hai nhóm: Surefire trong `target/surefire-reports/`, E2E Failsafe trong `target/failsafe-reports/`. Mốc chạy E2E sẽ được ghi tại `PLAN.md`; `mvn clean verify` không bật profile `e2e` nên không thực thi nhóm MySQL này.

## Bảo mật và giới hạn hiện tại

- Không commit credentials, token, secret thật hoặc chia sẻ trong log/minh chứng. `.env`, cấu hình local, `uploads/`, `target/` được ignore; `.gitignore` không bảo vệ dữ liệu đã commit.
- Không bật `DEBUG`, `TRACE` hoặc logging HTTP/SQL chi tiết khi dùng credentials thật: log response đăng nhập có thể chứa token. Script E2E tắt DEBUG/TRACE trong tiến trình chạy và phục hồi sau khi kết thúc.
- JWT stateless, BCrypt; CSRF tắt vì dùng Bearer, không dùng cookie/session auth. Chỉ tác giả sửa/xóa News; sort có whitelist.
- Ảnh được đọc công khai. Không có ownership riêng cho ảnh: mọi user có JWT có thể xóa ảnh không còn được bài nào tham chiếu.
- `imageUrl` là chuỗi tùy chọn; không xác minh mọi URL client nhập thuộc kho upload. Kiểm tra tham chiếu dùng `/uploads/{filename}`, gồm host cũ và query/fragment; không bảo đảm mọi biến thể URL tự nhập/percent-encoding.
- Kiểm tra tham chiếu DB và xóa file không nguyên tử; chưa bảo đảm chống gán ảnh đồng thời sau lúc kiểm tra.
- `max-swallow-size=-1` giúp client nhận JSON 413 khi upload bị từ chối, không tăng giới hạn file. Cần đánh giá lại cấu hình đọc bỏ body khi triển khai ngoài local.
- Lưu ảnh trên filesystem; không cloud storage, refresh token, admin/roles, frontend, bình luận hoặc entity Category. Swagger public phục vụ bài tập; `ddl-auto=update` dành cho môi trường local hiện tại.
