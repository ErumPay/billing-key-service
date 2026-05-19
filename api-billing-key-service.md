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
| `card_number`     | String | Y    | 카드번호            |
| `expiry_date`     | String | Y    | 유효기간 (YYMM)     |
| `cvc`             | String | Y    | 보안코드            |
| `password_2digit` | String | Y    | 비밀번호 앞 두 자리 |
| `birth_date`      | String | Y    | 생년월일 (YYMMDD)   |

### Logic

1. Pay Server로부터 카드 및 사용자 정보 수신
2. `idempotency_key` 생성 (형식: `{PG번호(3)}-{OP(3)}-{TIMESTAMP(14)}-{RANDOM(25)}`, 48자, OP 코드: `ISS`=발급/`DEL`=삭제, 예: `001-ISS-20260507082830-a1b2c3d4e5f6g7h8i9j0k1l2m`)
3. `pg_billing_keys` 테이블에 PENDING 행 INSERT (`idempotency_key`, `pay_card_id`, `status='PENDING'`)
   - `live_pay_card_id` UNIQUE 제약으로 동일 `pay_card_id`의 PENDING/ACTIVE 빌링키 중복 자동 차단 (DELETED/FAILED 행이 누적되어 있어도 신규 INSERT 가능)
4. IIN(카드번호 앞 6자리) 기반 카드사 식별
5. 카드사 토큰 발급 API 호출 (`idempotency_key` 동봉)
   - 참고: [카드사 토큰 발급](https://www.notion.so/35afef49d8d58060b8c3d1adcc1d992d?pvs=21)
6. 응답 처리 (카드사 응답코드/메시지는 DB 미저장, 호출 응답값만 그대로 Pay 서버에 전달)
   - **성공**: UUID v4 (하이픈 제외 32자) `billing_key` 생성 → 해당 행 UPDATE (`billing_key`, `card_token` AES-256 암호화 저장, `masked_number`, `card_company`, `status='ACTIVE'`)
   - **실패**: 해당 행 UPDATE (`status='FAILED'`)
   - **타임아웃**: 카드사 조회 API로 재확인 후 동일 분기, 조회도 실패 시 FAILED 처리
7. 민감 정보 파기 (`card_number`, `expiry_date`, `cvc`, `password_2digit`, `birth_date`)
8. Pay Server에 결과 반환

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pay_card_id`      | Long   | 페이 카드 ID      |
| `billing_key`      | String | 빌링키            |
| `masked_number`    | String | 마스킹 카드번호   |
| `card_company`     | String | 카드사            |
| `response_code`    | String | 응답코드   |
| `response_message` | String | 응답메시지 |

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

### Logic

1. Pay Server로부터 빌링키 삭제 요청 수신
2. `pg_billing_keys`에서 `pay_card_id` + `billing_key` 일치하는 `ACTIVE` 행 조회
3. `card_token` 복호화
4. 카드사 호출용 `idempotency_key` 생성 (OP: `DEL`, 동일 포맷 48자, 카드사 호출에만 사용·DB 미저장)
5. 카드사 토큰 삭제 API 호출 (`idempotency_key` 동봉)
   - 참고: [카드사 토큰 삭제](https://www.notion.so/358fef49d8d580aab5a8f0717f6ba83b?pvs=21)
6. 응답 처리 (카드사 응답코드/메시지는 DB 미저장, 호출 응답값만 그대로 Pay 서버에 전달)
   - **성공**: 해당 행 UPDATE (`status='DELETED'`, `updated_at` 자동 갱신)
   - **실패**: `status='ACTIVE'` 유지 (DB 변경 없음, 재시도 가능)
7. Pay Server에 결과 반환

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pay_card_id`      | Long   | 페이 카드 ID      |
| `billing_key`      | String | 빌링키            |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

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

### Logic

1. PG Server로부터 카드사 토큰 반환 요청 수신
2. `pg_billing_keys`에서 `billing_key` 일치하는 `ACTIVE` 행 조회
3. `card_token` 복호화
4. PG Server에 카드사 토큰 반환

### Response Body

| 필드           | 타입   | 설명        |
|----------------|--------|-------------|
| `billing_key`  | String | 빌링키      |
| `card_token`   | String | 카드사 토큰 |
| `card_company` | String | 카드사      |
