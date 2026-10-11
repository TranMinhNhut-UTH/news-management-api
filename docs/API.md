# API Reference

[README](../README.md) | [SETUP](SETUP.md) | [API](API.md) | [TESTING](TESTING.md) | [SECURITY](SECURITY.md)

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
