# Billing Key Service API 명세

## 목차

1. [PG 빌링키 발급](#1-pg-빌링키billing-key-발급)
2. [PG 빌링키 삭제](#2-pg-빌링키billing-key-삭제)
3. [카드사 토큰 반환](#3-카드사-토큰card-token-반환)

---

## 1. PG 빌링키(Billing Key) 발급

- **desc**: Pay Server의 card-service로부터 카드 및 사용자 정보를 받아 카드사 통신을 통한 빌링키 발급
- **method**: `POST`
- **path**: `/api/v1/billing-key/issue`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드              | 타입   | 필수 | 설명                |
|-------------------|--------|------|---------------------|
| `pay_card_id`     | Long   | Y    | 페이 카드 ID        |
| `card_number`     | String | Y    | 카드번호(16자리)    |
| `expiry_date`     | String | Y    | 유효기간 (YYMM)     |
| `cvc`             | String | Y    | 보안코드(3자리)     |
| `password_2digit` | String | Y    | 비밀번호 앞 두 자리 |
| `birth_date`      | String | Y    | 생년월일 (YYMMDD)   |

```json
{
    "pay_card_id":1001,
    "card_number":"8000111234567890",
    "expiry_date":"2912",
    "cvc":"123",
    "password_2digit":"12",
    "birth_date":"900101"
}
```

### Logic

1. **Pay Server로부터 카드 및 사용자 정보 수신**
2. **pay_card_id 기반 중복 요청 사전 검사**
    - PENDING/ACTIVE 상태인 토큰 조회
        - 존재 시: 저장된 응답 반환 (echo)
        - 미존재 시: 신규 발급 절차(3번~) 진행
3. **idempotency_key 생성**
    - 하이픈 포함 48자
    - 형식: {PG번호(3)}-{OP(3)}-{TIMESTAMP(14)}-{RANDOM(25)}
    - OP 코드: ISS (발급)
4. `pg_billing_keys 테이블에 PENDING 행 INSERT`
    - idempotency_key, pay_card_id, status='PENDING'
    - 테이블의 live_pay_card_id UNIQUE 제약으로 동일 pay_card_id의 PENDING/ACTIVE 빌링키 중복 자동 차단
    - UNKNOWN row는 reconciliation worker가 별도 정리 (재발급 허용)
5. `IIN 기반 카드사 식별`
    - 카드번호 앞 두 자리
6. `카드사 토큰 발급 API 호출`
    - idempotency_key 동봉
    - 참고: [카드사 토큰 발급](https://www.notion.so/35afef49d8d58060b8c3d1adcc1d992d?pvs=21)
7. `응답 처리`
    - 성공 응답 수신 시
        - UUID v4 기반 32자 billing_key 생성(하이픈 제외)
        - DB 업데이트 (billing_key, AES-256 암호화 card_token, masked_number, card_company, status ACTIVE)
    - 실패 응답 수신 시
        - DB 업데이트 (status FAILED)
    - 타임아웃 발생 시
        - 카드사 조회 API로 재확인
        - 조회 성공 시 성공/실패 로직 수행
        - 조회 실패(타임아웃) 시 DB 업데이트 (status UNKNOWN)
        - 이후 reconciliation을 통한 UNKNOWN -> FAILED 변경 (실패 처리)
8. `Pay Server에 결과 반환`

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pay_card_id`      | Long   | 페이 카드 ID      |
| `billing_key`      | String | 빌링키            |
| `masked_number`    | String | 마스킹 카드번호   |
| `card_company`     | String | 카드사            |
| `response_code`    | String | 응답코드   |
| `response_message` | String | 응답메시지 |

```json
{
    "pay_card_id": 1001,
    "billing_key": "57b2327a4d1247ac8ed143c9c80b8faf",
    "masked_number": "8000-****-****-7890",
    "card_company": "삼성카드",
    "response_code": "100",
    "response_message": "정상 처리되었습니다."
}
```

---

## 2. PG 빌링키(Billing Key) 삭제

- **desc**: Pay Server의 card-service로부터 빌링키를 받아 카드사 토큰 삭제 후 빌링키 비활성화 (소프트 삭제)
- **method**: `POST`
- **path**: `/api/v1/billing-key/delete`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드          | 타입   | 필수 | 설명         |
|---------------|--------|------|--------------|
| `pay_card_id` | Long   | Y    | 페이 카드 ID |
| `billing_key` | String | Y    | 빌링키       |

```json
{
  "pay_card_id": 12345,
  "billing_key": "3f1a2b3c4d5e6f708192a3b4c5d6e7f8"
}
```

### Logic

1. `Pay Server로부터 빌링키 삭제 요청 수신`
2. `pg_billing_keys에서 pay_card_id + billing_key 일치하는 ACTIVE 행 조회`
    - 미발견 시: HTTP 404 응답 ("ACTIVE 빌링키를 찾을 수 없습니다.")
3. `card_token 복호화`
4. `idempotency_key 생성`
    - 하이픈 포함 48자
    - 형식: {PG번호(3)}-{OP(3)}-{TIMESTAMP(14)}-{RANDOM(25)}
    - OP 코드: DEL (삭제)
    - 카드사 호출에만 사용, DB 미저장
5. `카드사 토큰 삭제 API 호출`
    - idempotency_key 동봉
    - 전달값: pg_id, card_company, card_token (평문)
    - 참고: [카드사 토큰 삭제](https://www.notion.so/358fef49d8d580aab5a8f0717f6ba83b?pvs=21)
6. `응답 처리`
    - 카드사 응답코드/메시지는 DB 미저장, 응답값만 그대로 Pay Server에 전달
    - 성공 응답 수신 시
        - 해당 행 UPDATE (status='DELETED', updated_at 자동 갱신)
    - 실패 응답 수신 시
        - status='ACTIVE' 유지 (DB 변경 없음, 재시도 가능)
    - 타임아웃 발생 시
        - status='ACTIVE' 유지 (DB 변경 없음, 재시도 가능)
        - Pay Server에 응답: response_code=null, response_message="카드사 통신 실패"
7. `Pay Server에 결과 반환`

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pay_card_id`      | Long   | 페이 카드 ID      |
| `billing_key`      | String | 빌링키            |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
  "pay_card_id": 12345,
  "billing_key": "3f1a2b3c4d5e6f708192a3b4c5d6e7f8",
  "response_code": "100",
  "response_message": "정상 처리되었습니다."
}
```

---

## 3. 카드사 토큰(Card Token) 반환

- **desc**: PG Server의 pg-payment로부터 빌링키를 받아 카드사 토큰 반환
- **method**: `POST`
- **path**: `/api/v1/billing-key/token-retrieve`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드          | 타입   | 필수 | 설명   |
|---------------|--------|------|--------|
| `billing_key` | String | Y    | 빌링키 |

```json
{
    "billing_key": "3a6914c643894e7a879e6141366f6bd3"
}
```

### Logic

1. `PG Server로부터 카드사 토큰 반환 요청 수신`
2. `pg_billing_keys에서 billing_key 일치하는 ACTIVE 행 조회`
    - 미발견 시: HTTP 404 응답 ("ACTIVE 빌링키를 찾을 수 없습니다.")
3. `card_token 복호화`
4. `PG Server에 카드사 토큰 반환`

### Response Body

| 필드           | 타입   | 설명        |
|----------------|--------|-------------|
| `billing_key`  | String | 빌링키      |
| `card_token`   | String | 카드사 토큰 |
| `card_company` | String | 카드사      |

```json
{
    "billing_key": "3a6914c643894e7a879e6141366f6bd3",
    "card_token": "5777475f7d34440497f13f5c9ae054a8",
    "card_company": "삼성카드"
}
```
