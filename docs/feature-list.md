# RemoteDirectoryManager 기능리스트

## 관련 문서

- 서비스 개요: [`../README.md`](../README.md)
- 유저 플로우: [`user-flow-chart.md`](user-flow-chart.md)
- API 문서: [`api-documentation.md`](api-documentation.md)
- DB 구조: [`database-table-diagram.md`](database-table-diagram.md)

## 공통 규칙

- 모든 경로는 저장소 루트(`app.storage.root-path`) 기준 상대 경로로 처리된다.
- 루트 경로는 `/` 로 표현된다.
- `/api/v1/health` 를 제외한 모든 `/api/v1/**` 요청은 `API-Key` 헤더가 필요하다.
- 디렉터리 자체는 DB에 저장하지 않는다. 파일 메타데이터와 태그 관계만 DB에 저장한다.
- 파일 메타데이터는 `files` 테이블에 저장되며, 태그는 `tags`, 파일-태그 연결은 `file_tags` 에 저장된다.
- 경로 정규화, 상위 경로 이탈 방지, 심볼릭 링크 차단은 `PathNormalizer`, `PathResolver` 정책에 의존한다.

## 1. 상태 확인 기능

- 동작: 클라이언트나 외부 시스템이 서비스 생존 여부를 확인할 때 `GET /api/v1/health` 를 호출하면 동작한다.
- 요구 데이터: 별도 입력 데이터는 필요 없다.
- 의존: `HealthController` 에 의존하며 인증이 필요 없다.
- 저장·반영: 파일시스템과 DB에 저장되는 값은 없다.
- 결과: 서비스가 응답 가능하면 상태 문자열을 반환한다.

## 2. API Key 인증 기능

- 동작: 보호된 API 호출 과정에서 `API-Key` 헤더를 보내면 서버가 SHA-256 해시 비교로 인증한다.
- 요구 데이터: 원본 API Key 문자열이 필요하다.
- 의존: `ApiKeyAuthenticationFilter`, `SecurityProperties`, `ApiKeyHasher` 에 의존한다.
- 저장·반영: 요청마다 헤더 값을 해시 비교만 하며 DB 저장은 없다.
- 결과: 인증 성공 시 다음 로직으로 진행되고, 실패 시 `401 Unauthorized` 와 오류 JSON을 반환한다.

## 3. 디렉터리 목록 조회 기능

- 동작: 탐색 화면에서 특정 경로를 열 때 `GET /api/v1/entries` 를 호출하면 하위 엔트리 목록이 반환된다.
- 요구 데이터: `path`, `includeHidden` 값이 필요하다.
- 의존: `FileQueryService`, `PathResolver`, `FileMetadataService` 에 의존한다.
- 저장·반영: 디렉터리 자체는 DB에 저장하지 않으며, 목록에 포함된 파일은 `files` 테이블의 활성 메타데이터를 함께 조회한다.
- 결과: 파일과 디렉터리 목록, 숨김 여부, 파일 크기, 수정일시, 파일 ID, 태그 목록을 반환한다.
- 비고: 지원하지 않는 엔트리나 심볼릭 링크는 목록에서 제외되고 로그만 남긴다.

## 4. 엔트리 상세 조회 기능

- 동작: 사용자가 파일 또는 디렉터리 하나를 선택했을 때 `GET /api/v1/entries/detail` 을 호출하면 상세 정보가 반환된다.
- 요구 데이터: 조회 대상 `path` 가 필요하다.
- 의존: `FileQueryService`, `FileMetadataService`, 파일시스템 속성 조회에 의존한다.
- 저장·반영: 파일 상세 조회 시 대상 파일의 메타데이터를 `files` 테이블 기준으로 동기화한다. 디렉터리 상세 조회는 DB 저장이 없다.
- 결과: 엔트리 타입, 상위 경로, 생성일시, 수정일시, MIME, 크기, 태그 등을 반환한다.

## 5. 디렉터리 생성 기능

- 동작: 사용자가 특정 경로 아래 새 폴더를 만들 때 `POST /api/v1/directories` 를 호출하면 동작한다.
- 요구 데이터: `parentPath`, `name` 이 필요하다.
- 의존: `FileCommandService`, `PathNormalizer`, `PathResolver` 에 의존한다.
- 저장·반영: 실제 디렉터리는 파일시스템에 생성되며 DB에는 별도 레코드가 저장되지 않는다.
- 결과: 생성된 디렉터리의 상대 경로를 반환한다.

## 6. 파일 업로드 기능

- 동작: 사용자가 파일을 업로드할 때 `POST /api/v1/files/upload` 를 호출하면 동작한다.
- 요구 데이터: `parentPath`, `conflictPolicy`, `file` multipart 데이터가 필요하다.
- 의존: `FileCommandService`, `PathNormalizer`, `PathResolver`, `FileMetadataService` 에 의존한다.
- 저장·반영: 업로드 파일은 먼저 대상 디렉터리의 임시 파일로 기록된 뒤 실제 위치로 이동한다. 이후 파일 메타데이터가 `files` 테이블에 저장 또는 갱신된다.
- 결과: 최종 저장된 파일 경로를 반환한다.
- 비고: `FAIL` 은 충돌 시 실패, `OVERWRITE` 는 기존 파일 교체, `AUTO_RENAME` 은 중복 시 새 이름으로 저장한다.

## 7. 항목 삭제 기능

- 동작: 사용자가 파일 또는 디렉터리를 삭제할 때 `DELETE /api/v1/entries` 를 호출하면 동작한다.
- 요구 데이터: 삭제 대상 `path` 가 필요하다.
- 의존: `FileCommandService`, `PathResolver`, `FileMetadataService` 에 의존한다.
- 저장·반영: 대상 파일 또는 디렉터리는 먼저 staging 경로로 이동한 뒤 트랜잭션 커밋 시 실제 삭제된다. 관련 파일 메타데이터는 `files.is_active = false` 로 비활성화된다.
- 결과: 성공 시 본문 없는 `204 No Content` 를 반환한다.
- 비고: 루트(`/`) 는 삭제할 수 없다.

## 8. 파일 다운로드 기능

- 동작: 사용자가 파일을 내려받을 때 `GET /api/v1/files/download` 를 호출하면 동작한다.
- 요구 데이터: 다운로드 대상 `path` 가 필요하다.
- 의존: `FileTransferService`, `PathResolver`, MIME 판별 로직에 의존한다.
- 저장·반영: 파일시스템의 실제 파일을 읽어 응답으로 전송하며 DB 저장은 없다.
- 결과: 첨부 다운로드용 응답 헤더와 함께 바이너리 파일을 반환한다.

## 9. 파일 스트리밍 기능

- 동작: 사용자가 미리보기나 미디어 재생을 할 때 `GET /api/v1/files/stream` 을 호출하면 동작한다.
- 요구 데이터: `path` 가 필요하며, 부분 전송이 필요하면 `Range` 헤더를 함께 보낸다.
- 의존: `FileTransferService`, Range 파싱 로직, `PathResolver` 에 의존한다.
- 저장·반영: 파일시스템의 실제 파일을 읽어 응답으로 전송하며 DB 저장은 없다.
- 결과: 전체 파일 또는 부분 콘텐츠(`206 Partial Content`)를 반환한다.
- 비고: 오디오, 비디오, PDF, 대용량 파일 미리보기에 적합하다.

## 10. 태그 목록 조회 기능

- 동작: 클라이언트가 전체 태그 선택지를 불러올 때 `GET /api/v1/tags` 를 호출하면 동작한다.
- 요구 데이터: 별도 본문 데이터는 없다.
- 의존: `FileTagService`, `TagRepository` 에 의존한다.
- 저장·반영: 저장 동작은 없고 `tags` 테이블에서 전체 태그를 조회한다.
- 결과: 태그 ID와 태그명을 정렬된 목록으로 반환한다.

## 11. 파일 태그 부여 기능

- 동작: 사용자가 파일에 기존 태그를 연결하거나 새 태그를 만들면서 연결할 때 `POST /api/v1/files/tags` 를 호출하면 동작한다.
- 요구 데이터: `path`, `tagIds`, `tagNames` 가 필요하다. 기존 태그만, 새 태그만, 둘 다 함께 전달할 수 있다.
- 의존: `FileTagService`, `FileMetadataService`, `FileEntryRepository`, `TagRepository` 에 의존한다.
- 저장·반영: 파일 메타데이터가 먼저 동기화되고, 새 태그가 필요하면 `tags` 테이블에 저장된다. 이후 파일-태그 연결이 `file_tags` 테이블에 저장된다.
- 결과: 대상 파일 ID, 파일 경로, 최종 태그 목록을 반환한다.

## 12. 파일 태그 제거 기능

- 동작: 사용자가 파일에 연결된 태그를 제거할 때 `DELETE /api/v1/files/tags` 를 호출하면 동작한다.
- 요구 데이터: `path`, 제거할 `tagIds` 가 필요하다.
- 의존: `FileTagService`, `FileMetadataService`, `FileEntryRepository` 에 의존한다.
- 저장·반영: 대상 파일 메타데이터를 동기화한 뒤 `file_tags` 연결 관계를 제거한다. 태그 마스터 데이터(`tags`)는 삭제하지 않는다.
- 결과: 대상 파일 ID, 파일 경로, 제거 후 남은 태그 목록을 반환한다.

## 13. 파일 메타데이터 동기화 기능

- 동작: 파일 상세 조회, 업로드, 태그 작업 과정에서 파일 메타데이터 최신화가 필요할 때 내부적으로 동작한다.
- 요구 데이터: 대상 파일 `path` 가 필요하다.
- 의존: `FileMetadataService`, `FileEntryRepository`, 파일시스템 속성 조회에 의존한다.
- 저장·반영: 파일 경로, 파일명, 확장자, 생성일시, 수정일시, 활성 여부가 `files` 테이블에 저장 또는 갱신된다.
- 결과: 최신 메타데이터 기준의 파일 엔티티가 반환된다.
- 비고: 삭제 후 같은 경로에 파일이 다시 생기면 기존 레코드를 재활성화하고 태그/썸네일 연결은 초기화한다.

## 14. 삭제 메타데이터 비활성화 기능

- 동작: 파일 또는 디렉터리 삭제가 성공적으로 진행될 때 내부적으로 관련 메타데이터를 비활성화한다.
- 요구 데이터: 삭제 대상 `path` 가 필요하다.
- 의존: `FileMetadataService`, `FileEntryRepository.deactivateByPathOrDescendant()` 에 의존한다.
- 저장·반영: 대상 파일 경로와 하위 경로에 해당하는 `files.is_active` 값이 `false` 로 갱신된다.
- 결과: 이후 목록 조회나 상세 조회 시 삭제된 파일 메타데이터는 활성 데이터로 취급되지 않는다.
