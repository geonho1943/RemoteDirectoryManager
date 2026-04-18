# RemoteDirectoryManager API 문서

## 관련 문서

- 기능 요약: [`feature-list.md`](feature-list.md)
- 유저 플로우: [`user-flow-chart.md`](user-flow-chart.md)
- DB 구조: [`database-table-diagram.md`](database-table-diagram.md)

## 1. 개요

- Base URL Prefix: `/api/v1`
- 루트 경로 표기: `/`
- 모든 경로는 저장소 루트 기준 상대 경로로 처리된다.
- `/api/v1/health` 를 제외한 모든 API는 `API-Key` 헤더 인증이 필요하다.

## 2. 인증 규칙

### 인증 헤더

```http
API-Key: {raw-api-key}
```

### 인증 동작

- 클라이언트는 원본 API Key 문자열을 헤더로 전송한다.
- 서버는 전달받은 값을 SHA-256 해시로 변환한 뒤 설정값과 비교한다.
- 인증 실패 시 `401 Unauthorized` 와 오류 JSON을 반환한다.

### 인증 제외 API

- `GET /api/v1/health`

## 3. 공통 응답 규칙

### 3.1 성공 응답

- `GET`: `200 OK`
- `POST /api/v1/directories`: `201 Created`
- `POST /api/v1/files/upload`: `201 Created`
- `DELETE /api/v1/entries`: `204 No Content`

### 3.2 공통 오류 응답 형식

```json
{
  "code": "ENTRY_NOT_FOUND",
  "message": "Entry not found: /docs/test.txt",
  "path": "/api/v1/entries/detail",
  "timestamp": "2026-04-18T14:20:00"
}
```

### 3.3 주요 오류 코드

- `INVALID_PATH`
- `INVALID_ENTRY_NAME`
- `ENTRY_NOT_FOUND`
- `ENTRY_ALREADY_EXISTS`
- `NOT_A_DIRECTORY`
- `NOT_A_FILE`
- `INVALID_MOVE_TARGET`
- `INVALID_RANGE_HEADER`
- `INVALID_TAG`
- `METADATA_SYNC_FAILED`
- `TRANSACTION_SYNCHRONIZATION_UNAVAILABLE`
- `FILE_OPERATION_FAILED`
- `UNAUTHORIZED_API_KEY`
- `INTERNAL_SERVER_ERROR`

## 4. 도메인별 API 목록

### 4.1 Health

| Method | Endpoint | 인증 | 기능 |
|---|---|---|---|
| `GET` | `/api/v1/health` | 불필요 | 서버 상태 확인 |

### 4.2 Entry

| Method | Endpoint | 인증 | 기능 |
|---|---|---|---|
| `GET` | `/api/v1/entries` | 필요 | 디렉터리 목록 조회 |
| `GET` | `/api/v1/entries/detail` | 필요 | 파일/디렉터리 상세 조회 |
| `DELETE` | `/api/v1/entries` | 필요 | 파일 또는 디렉터리 삭제 |

### 4.3 Directory

| Method | Endpoint | 인증 | 기능 |
|---|---|---|---|
| `POST` | `/api/v1/directories` | 필요 | 새 디렉터리 생성 |

### 4.4 File

| Method | Endpoint | 인증 | 기능 |
|---|---|---|---|
| `POST` | `/api/v1/files/upload` | 필요 | 파일 업로드 |
| `GET` | `/api/v1/files/download` | 필요 | 파일 다운로드 |
| `GET` | `/api/v1/files/stream` | 필요 | 파일 스트리밍 |

### 4.5 Tag

| Method | Endpoint | 인증 | 기능 |
|---|---|---|---|
| `GET` | `/api/v1/tags` | 필요 | 전체 태그 목록 조회 |
| `POST` | `/api/v1/files/tags` | 필요 | 파일에 태그 부여 |
| `DELETE` | `/api/v1/files/tags` | 필요 | 파일에서 태그 제거 |

## 5. 상세 API 문서

## 5.1 Health

### GET `/api/v1/health`

- 기능: 서버가 정상적으로 응답 가능한지 확인한다.
- 인증: 필요 없음
- Query Parameter: 없음
- Request Body: 없음

#### 응답 예시

```json
{
  "status": "HERE I AM"
}
```

## 5.2 Entry

### GET `/api/v1/entries`

- 기능: 특정 경로 아래의 파일과 디렉터리 목록을 조회한다.
- 인증: 필요
- 비고: 심볼릭 링크나 지원하지 않는 엔트리는 전체 요청을 실패시키지 않고 목록에서 제외된다.

#### Query Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 조회 대상 경로. 예: `/`, `/docs` |
| `includeHidden` | `boolean` | 아니오 | 숨김 파일 포함 여부. 기본값 `true` |

#### 요청 예시

```http
GET /api/v1/entries?path=/docs&includeHidden=true
API-Key: your-api-key
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `currentPath` | `string` | 현재 조회 경로 |
| `entries` | `array` | 하위 엔트리 목록 |

#### `entries[]` 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `entryType` | `string` | `DIRECTORY` 또는 `FILE` |
| `relativePath` | `string` | 엔트리 전체 상대 경로 |
| `parentPath` | `string` | 상위 경로 |
| `name` | `string` | 파일명 또는 디렉터리명 |
| `extension` | `string|null` | 파일 확장자 |
| `mimeType` | `string|null` | MIME 타입 |
| `sizeBytes` | `number|null` | 파일 크기. 디렉터리는 `null` |
| `modifiedAt` | `datetime` | 수정 시각 |
| `hidden` | `boolean` | 숨김 여부 |
| `fileId` | `number|null` | 파일 메타데이터 ID |
| `tags` | `array` | 연결된 태그 목록 |

#### 응답 예시

```json
{
  "currentPath": "/docs",
  "entries": [
    {
      "entryType": "DIRECTORY",
      "relativePath": "/docs/archive",
      "parentPath": "/docs",
      "name": "archive",
      "extension": null,
      "mimeType": null,
      "sizeBytes": null,
      "modifiedAt": "2026-04-18T14:20:00",
      "hidden": false,
      "fileId": null,
      "tags": []
    },
    {
      "entryType": "FILE",
      "relativePath": "/docs/spec.md",
      "parentPath": "/docs",
      "name": "spec.md",
      "extension": "md",
      "mimeType": "text/markdown",
      "sizeBytes": 2048,
      "modifiedAt": "2026-04-18T13:00:00",
      "hidden": false,
      "fileId": 12,
      "tags": [
        {
          "tagId": 1,
          "tagName": "important"
        }
      ]
    }
  ]
}
```

### GET `/api/v1/entries/detail`

- 기능: 선택한 파일 또는 디렉터리의 상세 정보를 조회한다.
- 인증: 필요

#### Query Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 상세 조회 대상 경로 |

#### 요청 예시

```http
GET /api/v1/entries/detail?path=/docs/spec.md
API-Key: your-api-key
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `entryType` | `string` | `DIRECTORY` 또는 `FILE` |
| `relativePath` | `string` | 엔트리 전체 상대 경로 |
| `parentPath` | `string` | 상위 경로 |
| `name` | `string` | 파일명 또는 디렉터리명 |
| `extension` | `string|null` | 파일 확장자 |
| `mimeType` | `string|null` | MIME 타입 |
| `sizeBytes` | `number|null` | 파일 크기 |
| `modifiedAt` | `datetime` | 수정 시각 |
| `createdAtFs` | `datetime|null` | 파일시스템 생성 시각 |
| `hidden` | `boolean` | 숨김 여부 |
| `fileId` | `number|null` | 파일 메타데이터 ID |
| `tags` | `array` | 연결된 태그 목록 |

#### 응답 예시

```json
{
  "entryType": "FILE",
  "relativePath": "/docs/spec.md",
  "parentPath": "/docs",
  "name": "spec.md",
  "extension": "md",
  "mimeType": "text/markdown",
  "sizeBytes": 2048,
  "modifiedAt": "2026-04-18T13:00:00",
  "createdAtFs": "2026-04-17T19:10:00",
  "hidden": false,
  "fileId": 12,
  "tags": [
    {
      "tagId": 1,
      "tagName": "important"
    }
  ]
}
```

### DELETE `/api/v1/entries`

- 기능: 파일 또는 디렉터리를 삭제한다.
- 인증: 필요
- Content-Type: `application/json`
- 비고: 실제 삭제 전 staging 경로로 이동한 뒤 메타데이터 비활성화와 함께 커밋 시 삭제가 확정된다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 삭제 대상 경로 |

#### 요청 예시

```json
{
  "path": "/docs/spec.md"
}
```

#### 응답

- 상태 코드: `204 No Content`
- 응답 본문: 없음

## 5.3 Directory

### POST `/api/v1/directories`

- 기능: 특정 경로 아래 새 디렉터리를 생성한다.
- 인증: 필요
- Content-Type: `application/json`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `parentPath` | `string` | 예 | 상위 경로 |
| `name` | `string` | 예 | 생성할 디렉터리명 |

#### 요청 예시

```json
{
  "parentPath": "/docs",
  "name": "drafts"
}
```

#### 응답 예시

```json
{
  "createdPath": "/docs/drafts"
}
```

## 5.4 File

### POST `/api/v1/files/upload`

- 기능: 파일을 업로드한다.
- 인증: 필요
- Content-Type: `multipart/form-data`
- 비고: 업로드 파일은 먼저 대상 디렉터리의 임시 파일로 기록되고, DB 반영 실패 시 롤백과 복구가 수행된다.

#### Multipart Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `parentPath` | `string` | 예 | 업로드 대상 디렉터리 |
| `conflictPolicy` | `string` | 예 | 충돌 정책: `FAIL`, `OVERWRITE`, `AUTO_RENAME` |
| `file` | `binary` | 예 | 업로드 파일 |

#### 요청 예시

```text
parentPath=/docs
conflictPolicy=AUTO_RENAME
file=<binary>
```

#### 응답 예시

```json
{
  "storedPath": "/docs/spec.md"
}
```

### GET `/api/v1/files/download`

- 기능: 파일을 첨부 다운로드 방식으로 반환한다.
- 인증: 필요

#### Query Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 다운로드 대상 파일 경로 |

#### 요청 예시

```http
GET /api/v1/files/download?path=/docs/spec.md
API-Key: your-api-key
```

#### 응답

- 상태 코드: `200 OK`
- 응답 본문: 바이너리 파일 스트림
- 주요 헤더
  - `Content-Type`
  - `Content-Length`
  - `Content-Disposition: attachment`

### GET `/api/v1/files/stream`

- 기능: 파일을 스트리밍 방식으로 반환한다.
- 인증: 필요

#### Query Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 스트리밍 대상 파일 경로 |

#### Header Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `Range` | `string` | 아니오 | 부분 콘텐츠 요청 범위. 예: `bytes=0-1023` |

#### 요청 예시

```http
GET /api/v1/files/stream?path=/media/demo.mp4
API-Key: your-api-key
Range: bytes=0-1048575
```

#### 응답

- 전체 전송 시: `200 OK`
- 부분 전송 시: `206 Partial Content`
- 응답 본문: 바이너리 파일 스트림
- 주요 헤더
  - `Content-Type`
  - `Content-Length`
  - `Content-Disposition: inline`
  - `Accept-Ranges: bytes`
  - `Content-Range` (부분 응답 시)

## 5.5 Tag

### GET `/api/v1/tags`

- 기능: 전체 태그 목록을 조회한다.
- 인증: 필요

#### Query Parameter

- 없음

#### 응답 예시

```json
{
  "tags": [
    {
      "tagId": 1,
      "tagName": "important"
    },
    {
      "tagId": 2,
      "tagName": "archive"
    }
  ]
}
```

### POST `/api/v1/files/tags`

- 기능: 파일에 기존 태그를 연결하거나 새 태그를 생성 후 연결한다.
- 인증: 필요
- Content-Type: `application/json`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 대상 파일 경로 |
| `tagIds` | `array<number>` | 아니오 | 연결할 기존 태그 ID 목록 |
| `tagNames` | `array<string>` | 아니오 | 새로 생성하며 연결할 태그명 목록 |

#### 요청 예시

```json
{
  "path": "/docs/spec.md",
  "tagIds": [1, 2],
  "tagNames": ["draft", "review"]
}
```

#### 응답 예시

```json
{
  "fileId": 12,
  "filePath": "/docs/spec.md",
  "tags": [
    {
      "tagId": 1,
      "tagName": "important"
    },
    {
      "tagId": 2,
      "tagName": "archive"
    },
    {
      "tagId": 7,
      "tagName": "draft"
    }
  ]
}
```

### DELETE `/api/v1/files/tags`

- 기능: 파일에 연결된 태그를 제거한다.
- 인증: 필요
- Content-Type: `application/json`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `path` | `string` | 예 | 대상 파일 경로 |
| `tagIds` | `array<number>` | 예 | 제거할 태그 ID 목록 |

#### 요청 예시

```json
{
  "path": "/docs/spec.md",
  "tagIds": [2, 7]
}
```

#### 응답 예시

```json
{
  "fileId": 12,
  "filePath": "/docs/spec.md",
  "tags": [
    {
      "tagId": 1,
      "tagName": "important"
    }
  ]
}
```

## 6. 충돌 정책 정의

### `FAIL`

- 같은 이름 파일이 이미 존재하면 업로드를 실패 처리한다.

### `OVERWRITE`

- 같은 이름 파일이 존재하면 기존 파일을 교체한다.

### `AUTO_RENAME`

- 같은 이름 파일이 존재하면 자동으로 새 이름을 생성해 저장한다.

## 7. API 사용 시 유의사항

- 디렉터리 생성과 삭제는 파일시스템에만 직접 반영된다.
- 파일 관련 메타데이터와 태그는 DB에 관리된다.
- 파일 상세 조회, 태그 작업, 업로드 과정에서 파일 메타데이터 동기화가 발생할 수 있다.
- `DELETE /api/v1/entries` 는 파일과 디렉터리 모두 처리할 수 있다.
- `GET /api/v1/files/stream` 은 미리보기와 미디어 재생에 적합하다.
- `GET /api/v1/files/download` 는 브라우저 다운로드 동작에 적합하다.
- 목록 조회 시 일부 문제 엔트리가 있더라도 가능한 항목은 계속 반환한다.
