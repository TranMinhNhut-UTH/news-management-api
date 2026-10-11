# Testing

[README](../README.md) | [SETUP](SETUP.md) | [API](API.md) | [TESTING](TESTING.md) | [SECURITY](SECURITY.md)

## Automated tests

Từ thư mục chứa `pom.xml`:

```powershell
mvn clean verify
```

Kết quả đã kiểm chứng ngày **11/10/2026**: **97/97 tests PASS**, không failures/errors/skipped. Tests bao phủ DTO/error response, BCrypt/JWT, public/private, ownership, News CRUD/search/sort/page, upload/đọc/xóa ảnh và OpenAPI. Có file ảnh/thư mục tạm và Tomcat thật; repository được mock. Nhánh symlink/lỗi quyền IO được mô phỏng để chạy trên Windows không có quyền tạo symlink.

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

Kết quả Maven tách hai nhóm: Surefire trong `target/surefire-reports/`, E2E Failsafe trong `target/failsafe-reports/`. `mvn clean verify` không bật profile `e2e` nên không thực thi nhóm MySQL này. Script nằm tại [scripts/run-e2e.ps1](../scripts/run-e2e.ps1).

| Lần kiểm chứng 11/10/2026 | PASS | FAIL | ERROR | SKIPPED |
| --- | ---: | ---: | ---: | ---: |
| `mvn clean verify` | 97 | 0 | 0 | 0 |
| `mvn clean verify -Pe2e` | 102 (97 + 5 E2E) | 0 | 0 | 0 |

Đây là kết quả chạy đã ghi nhận, không phải CI tự động. Tiến độ được lưu riêng tại [PLAN.md](../PLAN.md). Bộ E2E kiểm tra pagination size=1 trên hai bài; chưa kiểm chứng dataset trên 10 bài, persistence sau restart, collation/dấu tiếng Việt, JWT hết hạn hoặc toàn bộ lỗi upload với MySQL thật. Các tests không dùng DB bao phủ thêm nhánh JWT và upload; không suy ra coverage phần trăm từ số tests.
