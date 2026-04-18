# RemoteDirectoryManager

RemoteDirectoryManager는 파일시스템 저장소를 기준으로 파일과 디렉터리를 관리하고,  
파일 메타데이터와 태그를 MariaDB에 함께 관리하는 API 중심 원격 파일 관리 서비스입니다.

실제 파일 본문과 디렉터리 구조는 파일시스템에 저장되고, DB에는 파일 메타데이터와 태그 관계만 저장됩니다.  
인증은 `API-Key` 헤더 기반으로 처리되며, 운영 환경에서는 API 중심으로 사용하는 구성을 전제로 합니다.  
디렉터리는 DB에 별도 엔터티로 저장하지 않으며, 실제 파일시스템 조회 결과를 기준으로 처리합니다.

## 핵심 특징

- 저장소 루트 기준 상대 경로만 사용
- 디렉터리 목록 조회, 상세 조회, 생성, 업로드, 삭제 지원
- 파일 다운로드와 HTTP Range 기반 스트리밍 지원
- 파일 메타데이터 동기화 및 태그 관리 지원
- API Key 인증으로 보호된 API 접근 제어
- 업로드/삭제 시 staging 기반 보상 처리로 파일시스템과 DB 정합성 보강
- 문제 엔트리 발생 시 전체 목록 실패 대신 해당 항목만 제외하고 계속 조회

## 문서

| 문서 | 설명 |
|---|---|
| [`docs/feature-list.md`](docs/feature-list.md) | 기능별 동작, 요구 데이터, 의존성, 저장 위치 요약 |
| [`docs/user-flow-chart.md`](docs/user-flow-chart.md) | 사용자 진입 흐름, 탐색 흐름, 파일 작업 흐름 다이어그램 |
| [`docs/api-documentation.md`](docs/api-documentation.md) | 도메인별 API 엔드포인트, 파라미터, 응답 규칙 |
| [`docs/database-table-diagram.md`](docs/database-table-diagram.md) | DB 테이블 구조, 관계, 저장 흐름 ERD |

## 기술 스택

- Java 21
- Spring Boot 3.5.x
- Gradle
- MariaDB

## 필수 설정

아래 설정값이 필요합니다.

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `app.storage.root-path`
- `app.security.admin-key-hash`

`app.security.admin-key-hash`에는 원본 API Key를 SHA-256으로 변환한 소문자 16진수 해시값을 넣어야 합니다.

## API 요약

- `GET /api/v1/health`
- `GET /api/v1/entries`
- `GET /api/v1/entries/detail`
- `DELETE /api/v1/entries`
- `POST /api/v1/directories`
- `POST /api/v1/files/upload`
- `GET /api/v1/files/download`
- `GET /api/v1/files/stream`
- `GET /api/v1/tags`
- `POST /api/v1/files/tags`
- `DELETE /api/v1/files/tags`

자세한 요청/응답 형식은 [`docs/api-documentation.md`](docs/api-documentation.md)를 참고할 수 있습니다.

## 프로젝트 구조

```text
src/main/java/com/example/fileserver
├─ common
│  ├─ error
│  ├─ response
│  └─ time
├─ config
├─ entry
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ repository
│  └─ service
├─ filesystem
│  └─ path
├─ health
├─ security
└─ transfer
   ├─ controller
   └─ service
```

## 데이터베이스

기본 스키마는 [`src/main/resources/schema.sql`](src/main/resources/schema.sql)에 정의되어 있으며,  
`files`, `thumbnails`, `tags`, `file_tags` 테이블을 사용합니다.

구조와 관계는 [`docs/database-table-diagram.md`](docs/database-table-diagram.md)에서 ERD로 확인할 수 있습니다.

## 라이선스

이 프로젝트는 [`MIT License`](LICENSE)를 따릅니다.
