# Bảo mật và giới hạn

[README](../README.md) | [SETUP](SETUP.md) | [API](API.md) | [TESTING](TESTING.md) | [SECURITY](SECURITY.md)

## Bảo mật và giới hạn hiện tại

- Không commit credentials, token, secret thật hoặc chia sẻ trong log/minh chứng. `.env`, cấu hình local, `uploads/`, `target/` được ignore; `.gitignore` không bảo vệ dữ liệu đã commit.
- Không bật `DEBUG`, `TRACE` hoặc logging HTTP/SQL chi tiết khi dùng credentials thật: log response đăng nhập có thể chứa token. Script E2E tắt DEBUG/TRACE trong tiến trình chạy và phục hồi sau khi kết thúc.
- JWT stateless, BCrypt; CSRF tắt vì dùng Bearer, không dùng cookie/session auth. Chỉ tác giả sửa/xóa News; sort có whitelist.
- Ảnh được đọc công khai. Không có ownership riêng cho ảnh: mọi user có JWT có thể xóa ảnh không còn được bài nào tham chiếu.
- `imageUrl` là chuỗi tùy chọn; không xác minh mọi URL client nhập thuộc kho upload. Kiểm tra tham chiếu dùng `/uploads/{filename}`, gồm host cũ và query/fragment; không bảo đảm mọi biến thể URL tự nhập/percent-encoding.
- Kiểm tra tham chiếu DB và xóa file không nguyên tử; chưa bảo đảm chống gán ảnh đồng thời sau lúc kiểm tra.
- `max-swallow-size=-1` giúp client nhận JSON 413 khi upload bị từ chối, không tăng giới hạn file. Cần đánh giá lại cấu hình đọc bỏ body khi triển khai ngoài local.
- Lưu ảnh trên filesystem; không cloud storage, refresh token, admin/roles, frontend, bình luận hoặc entity Category. Swagger public phục vụ bài tập; `ddl-auto=update` dành cho môi trường local hiện tại.
