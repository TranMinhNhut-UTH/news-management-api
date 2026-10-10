# News Management API — NV17

Đã triển khai Phase 1–3: cấu hình project, API Foundation và register/login với JWT. Chưa triển khai News CRUD/search hoặc upload/delete ảnh.

Dependencies dùng Spring Boot 3.5.16, springdoc-openapi 2.8.17 và JJWT 0.13.0. Tham khảo [yêu cầu Java của Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [bảng tương thích springdoc](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) và [JJWT](https://github.com/jwtk/jjwt).

## Yêu cầu

- JDK 21, Maven 3.6.3 trở lên, MySQL 8.
- Tạo database `news_management` trong MySQL và tài khoản có quyền tạo/cập nhật bảng trong database này. JPA `ddl-auto=update` quản lý bảng; không tự tạo database.
- Thiết lập biến môi trường theo `.env.example`. File này chỉ là mẫu, không được Spring Boot tự đọc.

## Chạy local bằng PowerShell

Thiết lập biến trong cùng terminal dùng để chạy Maven (hoặc cấu hình Environment Variables trong IDE):

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/news_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'
$env:DB_USERNAME = 'news_app'
$env:DB_PASSWORD = '<mat-khau-MySQL-local>'
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:APP_BASE_URL = 'http://localhost:8080'
mvn spring-boot:run
```

Các biến tùy chọn: `SERVER_PORT=8080`, `UPLOAD_DIR=./uploads`, `JWT_EXPIRATION_MS=3600000`. Không commit mật khẩu/secret thực tế.

## Build và kiểm tra

```powershell
java -version
mvn -version
mvn clean verify
```

`verify` chạy unit test cho API Foundation, BCrypt, JWT và integration test register/login/security/Swagger qua MockMvc với repository được mock; sau đó đóng gói JAR. Các test không cần database và không xác nhận mapping trên MySQL thực tế. Endpoint `/test/private` chỉ tồn tại trong test.

## Authentication & JWT — Phase 3

- `JWT_SECRET` bắt buộc, không có mặc định; tối thiểu 32 byte UTF-8. Lệnh trên tạo secret ngẫu nhiên cho local. Chuỗi Base64 được dùng trực tiếp làm key UTF-8, không decode Base64. Giữ cùng secret giữa các lần chạy nếu muốn token cũ tiếp tục hợp lệ; thay secret sẽ làm token cũ không hợp lệ. Không dùng secret test hoặc commit secret thật.
- `JWT_EXPIRATION_MS` mặc định `3600000` (1 giờ), tối thiểu `1000`. Token HS256 có subject là username, issued-at và expiration. `expiresIn` trong response tính bằng giây (mặc định `3600`); JWT lưu timestamps với độ chính xác giây.
- Mật khẩu đăng ký dài 8–72 ký tự và tối đa 72 byte UTF-8 theo giới hạn BCrypt. Username giữ nguyên như request; uniqueness được bảo vệ bằng check repository và unique constraint MySQL (so sánh theo collation của DB).
- Security stateless, không tạo session, tắt CSRF vì chỉ dùng Bearer token; không bật form login hoặc HTTP Basic.
- Public: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. Các đường dẫn còn lại yêu cầu JWT trong Phase 3; cấu hình GET public cho News sẽ làm ở Phase 4.
- Token thiếu ở đường dẫn private, sai chữ ký, sai định dạng, hết hạn hoặc user không còn tồn tại trả 401 với `{success, message, data}`. Authorization header được gửi nhưng sai cũng trả 401 trên public endpoint; khi register/login hãy bỏ token cũ khỏi request. Lỗi validation trả 400 và `errors`; username trùng trả 409; sai username/password trả 401.

### Test Swagger/Postman với MySQL local

1. Cấu hình `DB_*`, `JWT_SECRET` trong cùng terminal/IDE, chạy `mvn spring-boot:run`.
2. Mở `http://localhost:8080/swagger-ui/index.html` hoặc tạo request Postman. POST `http://localhost:8080/api/v1/auth/register`, Content-Type `application/json`:

```json
{"username":"writer","password":"password123"}
```

Kỳ vọng 201 với `data.id`, `data.username`, không có password/hash. Gửi lại username đó phải trả 409; username rỗng/password ngắn trả 400.

3. POST `http://localhost:8080/api/v1/auth/login` với cùng JSON. Kỳ vọng 200:

```json
{"success":true,"message":"Logged in successfully","data":{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":3600}}
```

4. Swagger: nhấn **Authorize**, dán phần JWT (không thêm `Bearer`). Postman: Authorization → Bearer Token, dán JWT. Sai password hoặc username không tồn tại phải trả 401.
5. Kiểm tra DB trong Workbench: `SELECT id, username, password_hash FROM users;` và xác nhận password_hash là BCrypt, không phải plaintext; không chia sẻ hash trong API/log. Gửi GET `/api/v1/news` không token phải trả 401. Với token hợp lệ, route hiện chưa triển khai nên trả 404, không phải danh sách tin. Các test MockMvc đã xác nhận truy cập private bằng token hợp lệ.
6. Kiểm tra hết hạn: đặt `JWT_EXPIRATION_MS=5000`, khởi động lại và login lấy token mới, chờ hơn 5 giây rồi gọi private route; kỳ vọng 401 `Token expired`. Sửa một ký tự trong token hoặc dùng token từ secret khác phải trả 401. Khôi phục thời hạn mặc định sau khi test.

Nếu IDE kết nối MySQL được nhưng terminal thiếu `DB_*`, chạy các bước này bằng cấu hình IDE hiện có hoặc export biến theo phần trên; không cần đổi database để chạy test.

## Kiểm tra MySQL thủ công cho Phase 2

1. Bảo đảm MySQL đang chạy và `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` có trong môi trường của terminal/IDE chạy ứng dụng. Nếu IDE chạy được nhưng terminal không chạy được, kiểm tra lại Environment Variables của hai nơi.
2. Chạy `mvn spring-boot:run`, kiểm tra log khởi động thành công và không có lỗi kết nối/schema. JPA `ddl-auto=update` sẽ tạo/cập nhật bảng `users`, `news`.
3. Trong MySQL Workbench hoặc MySQL CLI, chạy:

```sql
USE news_management;
SHOW CREATE TABLE users;
SHOW CREATE TABLE news;
SHOW INDEX FROM news;
```

Kiểm tra username unique, password_hash không null, content LONGTEXT, image_url nullable, author_id là foreign key tới users.id, created_at/updated_at DATETIME không null và các index author_id/created_at/category. Đây là kiểm tra mapping thực tế mà unit test không thay thế.

Response thành công/lỗi cơ bản dùng `{success, message, data}`; lỗi Bean Validation thêm `errors` theo field, không trả giá trị bị từ chối. PageResponse dùng page bắt đầu từ 0. UserResponse chỉ chứa id/username; NewsResponse chứa author dạng UserResponse. Quan hệ một User → nhiều News được biểu diễn bằng ManyToOne từ News, không cần collection ngược trong User.

GlobalExceptionHandler xử lý lỗi trong Spring MVC: lỗi dữ liệu request 400, không tìm thấy 404, không có quyền nghiệp vụ 403, đăng nhập sai 401, xung đột 409 và lỗi không dự kiến 500 (không lộ chi tiết nội bộ). SecurityErrorHandler trả response cùng cấu trúc cho lỗi 401/403 ở security filter. Cấu hình theo [Spring Security stateless](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html); ký/kiểm tra JWT dùng [JJWT](https://github.com/jwtk/jjwt).

Các package chưa triển khai tiếp tục giữ bằng `package-info.java`; không thay đổi Java 21 hoặc Spring Boot 3.5.16.
