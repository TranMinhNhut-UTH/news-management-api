# News Management API — NV17

Hiện tại chỉ triển khai Phase 1: Maven project Java 21, package structure, dependencies, cấu hình và constants. Chưa có entity hoặc API nghiệp vụ.

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

Phase 1 chưa có test nghiệp vụ; `verify` kiểm tra biên dịch và đóng gói JAR, không xác nhận kết nối MySQL. Kiểm tra khởi động với MySQL bằng `mvn spring-boot:run`; dừng bằng Ctrl+C.

Do đã thêm Spring Security dependency nhưng chưa triển khai Phase 3, ứng dụng dùng security mặc định của Spring Boot. JWT và cấu hình public/private API sẽ triển khai sau.

Không có entity ở Phase 1 nên chưa có bảng User/News để JPA tạo. Các package trống được lưu bằng `package-info.java` để giữ cấu trúc trong Git.
