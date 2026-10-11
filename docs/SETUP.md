# Thiết lập local

[README](../README.md) | [SETUP](SETUP.md) | [API](API.md) | [TESTING](TESTING.md) | [SECURITY](SECURITY.md)

## Công nghệ và môi trường

- **JDK 21**, **Maven 3.6.3+**, **MySQL 8**.

```powershell
java -version
mvn -version
```

Maven phải dùng Java 21. Không cần frontend để chạy API.

## Cấu trúc dự án

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

Cấu hình hiện tại dùng [application.yml](../src/main/resources/application.yml); không có `application.properties`.

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

[.env.example](../.env.example) chỉ là mẫu; **Spring Boot không tự đọc `.env`**. Thiết lập biến trong terminal chạy Maven hoặc Environment Variables của cấu hình chạy IDE.

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
