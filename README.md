# News Management API — NV17

Đã triển khai Phase 1 và Phase 2 (API Foundation): cấu hình project, entity User/News, repository cơ bản, DTO validation, response chuẩn và global exception handler. Chưa có controller/API nghiệp vụ hoặc JWT.

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
$env:JWT_SECRET = '<secret-ngau-nhien-toi-thieu-32-byte>'
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

`verify` chạy unit test cho validation, serialization không lộ mật khẩu/hash, phân trang, callbacks timestamps và exception handler qua MockMvc; sau đó đóng gói JAR. Các test không cần database và không xác nhận mapping trên MySQL thực tế. Controller trong test chỉ là fixture, không tạo API production.

Do đã thêm Spring Security dependency nhưng chưa triển khai Phase 3, ứng dụng dùng security mặc định của Spring Boot. JWT và cấu hình public/private API sẽ triển khai sau.

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

GlobalExceptionHandler xử lý lỗi trong Spring MVC: lỗi dữ liệu request 400, không tìm thấy 404, không có quyền nghiệp vụ 403, xung đột 409 và lỗi không dự kiến 500 (không lộ chi tiết nội bộ). Lỗi security filter và chuẩn hóa response đăng nhập/JWT thuộc Phase 3, chưa triển khai ở đây.

Các package chưa triển khai tiếp tục giữ bằng `package-info.java`; không thay đổi Java 21 hoặc Spring Boot 3.5.16.
