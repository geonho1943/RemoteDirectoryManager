# RemoteDirectoryManager

RDM은 가용 가능한 파일시스템을 기준으로 파일과 폴더를 관리할 수 있는\
개인용 `파일 관리 서비스`를 지원 합니다.

실제 파일 저장소를 기준으로 동작하고 관리, 조회와 동기화를 위한 메타데이터를 저장하는 DB를 추가 하였습니다\
간단한 조작으로 다양한 기능을 사용할 수 있는 api지원으로\
더 나은 사용자 경험과 신뢰성을 제공 합니다\

## 주요 특징

- 루트 디렉토리 기준 상대 경로만 사용
- 디렉토리 조회, 상세 조회, 생성, 업로드, 삭제 지원
- 파일 다운로드와 HTTP Range 기반 스트리밍 지원 (추가 예정)
- API Key 인증 적용으로 타인의 접근 제한
- 문제시 간편하게 대응 가능

## 동작 방식

- 관리 대상은 설정한 루트 디렉토리 아래의 파일과 폴더입니다.
- DB에는 절대 경로가 아니라 서비스 내부 상대 경로만 저장합니다.
- 경로 입력은 모두 정규화한 뒤 처리합니다.

## 기술 구성

- Java 21
- Spring Boot 3.5.x
- Gradle
- MariaDB

## 필수 설정 값

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `FILESYSTEM_ROOT_PATH`
- `ADMIN_KEY_HASH`

`ADMIN_KEY_HASH`에는 실제 API Key를 SHA-256으로 변환한 값을 넣어야 합니다.

## API

### 상태 확인

- `GET /api/v1/health`

### 조회

- `GET /api/v1/entries`
  - 디렉토리의 자식 자원을 조회합니다.
- `GET /api/v1/entries/detail`
  - 파일 또는 폴더 하나의 상세 정보를 조회합니다.

### 디렉토리 생성

- `POST /api/v1/directories`

### 파일 업로드

- `POST /api/v1/files/upload`

### 삭제

- `DELETE /api/v1/entries`

### 파일 전송

- `GET /api/v1/files/download`
- `GET /api/v1/files/stream`

## 응답과 인증

- `/api/v1/health`를 제외한 `/api/v1/**` 요청에는 인증이 필요합니다.
- 인증 실패나 경로 오류, 파일 처리 오류는 공통 JSON 형식으로 응답합니다.
- 스트리밍 API는 `Range` 헤더를 처리합니다.

## 프로젝트 구조

```text
src/main/java/com/example/fileserver
├─ common
│  ├─ error
│  └─ response
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

기본 메타데이터 테이블은 `file_entries` 하나를 사용합니다.  
스키마는 [`src/main/resources/schema.sql`](src/main/resources/schema.sql)에 있습니다.

## 라이선스

MIT License

```text
Copyright (c) 2026

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
